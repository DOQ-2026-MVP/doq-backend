package com.doq.comfozi.ingestion.api

import com.doq.comfozi.ingestion.service.IngestionChange
import com.doq.comfozi.ingestion.service.IngestionChanged
import com.doq.comfozi.ingestion.service.IngestionReadService
import com.doq.comfozi.ingestion.service.IngestionSessionStatus
import com.doq.common.config.AsyncConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 인입 세션 현황 스트림 — 세션별 SSE 구독자를 들고 있다가 변화가 커밋되면 현황을 내보낸다.
 *
 * 흐르는 것은 델타가 아니라 **그때의 현황 전체**([IngestionStateEvent])다. 그래서 순서·유실을 따질 게
 * 없고, 끊겼다 붙어도 첫 이벤트가 곧 최신 상태라 놓친 구간을 메우는 장치(Last-Event-ID 재생)가 필요 없다.
 *
 * 전달은 **커밋 이후**([TransactionPhase.AFTER_COMMIT])이므로 아직 커밋되지 않은 변화가 새어 나가지 않고,
 * **다른 스레드**([AsyncConfig.INGESTION_EVENT_EXECUTOR])라 느린 구독자가 업로드 요청을 붙잡지 않는다.
 * 그 실행기는 스레드 1개다 — 이벤트 순서를 보장하기 위해서다.
 *
 * **한 인스턴스 안에서만 동작한다.** 구독자는 자기가 붙은 인스턴스에서 일어난 변화만 본다. 다중화하면
 * 인스턴스 간 팬아웃(Redis pub/sub 등)이 필요하다 — 지금은 단일 인스턴스 전제라 넣지 않았다.
 */
@Component
class IngestionEventStream(
    private val readService: IngestionReadService,
    @Value("\${app.ingestion.events.timeout-millis:1800000}") private val timeoutMillis: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 세션 id → 구독자들. 마지막 구독자가 떠나면 키까지 지운다(세션 수만큼 새는 것 방지). */
    private val subscribers = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    /**
     * 세션 현황 구독 시작 — **최초 스냅샷을 즉시** 보낸다.
     *
     * 스냅샷을 먼저 주는 덕분에 구독 직전에 일어난 변화를 놓치지 않는다(별도 GET 과의 경쟁 없음).
     * 세션이 없으면 [IngestionReadService.getStatus]가 예외를 던져 404 로 나간다 — emitter 를 만들기 전이다.
     */
    fun subscribe(ingestionId: Long): SseEmitter {
        val snapshot = event(readService.getStatus(ingestionId), change = null)

        val emitter = SseEmitter(timeoutMillis)
        emitter.onCompletion { remove(ingestionId, emitter) }
        emitter.onTimeout { remove(ingestionId, emitter) } // 타임아웃 후 재구독하면 다시 스냅샷부터
        emitter.onError { remove(ingestionId, emitter) }

        subscribers.computeIfAbsent(ingestionId) { CopyOnWriteArrayList() }.add(emitter)
        if (!send(emitter, snapshot)) {
            remove(ingestionId, emitter)
        }
        return emitter
    }

    @Async(AsyncConfig.INGESTION_EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onChanged(changed: IngestionChanged) {
        val emitters = subscribers[changed.ingestionId] ?: return // 보는 사람이 없으면 읽지도 않는다

        val state = event(readService.getStatus(changed.ingestionId), changed.change)
        emitters.forEach { emitter ->
            if (!send(emitter, state)) {
                remove(changed.ingestionId, emitter)
            }
        }
    }

    /**
     * 살아 있음 표시 — 조용한 세션의 연결이 프록시 idle 타임아웃에 끊기지 않게 한다.
     * SSE 주석(`:`)이라 클라이언트 이벤트 핸들러에는 걸리지 않는다.
     */
    @Scheduled(fixedDelayString = "\${app.ingestion.events.heartbeat-millis:15000}")
    fun heartbeat() {
        subscribers.forEach { (ingestionId, emitters) ->
            emitters.forEach { emitter ->
                if (!send(emitter) { it.send(SseEmitter.event().comment("keep-alive")) }) {
                    remove(ingestionId, emitter)
                }
            }
        }
    }

    private fun event(status: IngestionSessionStatus, change: IngestionChange?) =
        IngestionStateEvent(
            state = IngestionState(status),
            change = change?.let(IngestionChangeResponse::of),
        )

    private fun send(emitter: SseEmitter, state: IngestionStateEvent): Boolean =
        send(emitter) { it.send(SseEmitter.event().name(STATE_EVENT).data(state)) }

    /**
     * 끊긴 구독자는 정상적인 일이므로(탭 닫기 등) 실패를 예외로 올리지 않고 false 로 알린다 —
     * 호출부가 목록에서 빼면 끝이다.
     */
    private fun send(emitter: SseEmitter, write: (SseEmitter) -> Unit): Boolean =
        try {
            write(emitter)
            true
        } catch (e: Exception) {
            log.debug("SSE 전송 실패 — 구독 해제", e)
            emitter.completeWithError(e)
            false
        }

    private fun remove(ingestionId: Long, emitter: SseEmitter) {
        subscribers.computeIfPresent(ingestionId) { _, emitters ->
            emitters.remove(emitter)
            if (emitters.isEmpty()) null else emitters // 마지막이면 키까지 제거
        }
    }

    private companion object {
        /** SSE `event:` 이름 — 종류가 하나뿐이라 클라이언트는 이것만 듣는다. */
        const val STATE_EVENT = "state"
    }
}
