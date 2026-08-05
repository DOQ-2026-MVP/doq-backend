package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.service.IngestionRecordsAppended
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 인입 세션별 SSE 구독 허브 — 레코드 추가 이벤트를 구독 중인 스트림에 라이브 전파한다.
 *
 * 관찰자(pub/sub): 쓰기 서비스가 [IngestionRecordsAppended]를 발행하면 커밋 후 해당 세션 구독자에게 `record` 이벤트 전송.
 * 단일 인스턴스 in-process 전제 — 멀티 인스턴스는 외부 pub/sub(Redis 등) 필요.
 */
@Component
class IngestionSseHub {

    private val emittersBySession = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    /** 세션 스트림 구독 — [timeoutMs] 후 타임아웃. */
    fun subscribe(ingestionId: Long, timeoutMs: Long): SseEmitter {
        val emitter = SseEmitter(timeoutMs)
        register(ingestionId, emitter)
        return emitter
    }

    /** emitter 등록 + 종료/타임아웃/에러 시 정리(누수 방지). */
    internal fun register(ingestionId: Long, emitter: SseEmitter) {
        emittersBySession.computeIfAbsent(ingestionId) { CopyOnWriteArrayList() }.add(emitter)
        emitter.onCompletion { remove(ingestionId, emitter) }
        emitter.onError { remove(ingestionId, emitter) }
        emitter.onTimeout { emitter.complete() }
    }

    /** 커밋 후 해당 세션 구독자들에게 추가된 행을 `record` 이벤트로 전송. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onRecordsAppended(event: IngestionRecordsAppended) {
        val emitters = emittersBySession[event.ingestionId] ?: return
        val payloads = event.records.map(::IngestionRecordResponse)
        emitters.forEach { emitter ->
            try {
                payloads.forEach { emitter.send(SseEmitter.event().name("record").data(it)) }
            } catch (e: Exception) {
                remove(event.ingestionId, emitter)
                emitter.completeWithError(e)
            }
        }
    }

    private fun remove(ingestionId: Long, emitter: SseEmitter) {
        emittersBySession[ingestionId]?.remove(emitter)
    }
}
