package com.doq.comfozi.structuring

import com.doq.comfozi.ingestion.service.IngestionService
import com.doq.common.config.AsyncConfig
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 인계 실행 시점 담당 — 구조화 계산이 **커밋된 뒤**(AFTER_COMMIT) **다른 스레드**에서 인계를 돌린다.
 *
 * 요청 트랜잭션에서 떼어낸 이유: 3만 행짜리 세션이면 검수 레코드 3만 건 INSERT 가 요청 트랜잭션에
 * 매달려 그동안 락과 커넥션을 잡는다. 이제 요청 쪽은 읽기 전용으로 짧게 끝난다.
 *
 * 트랜잭션 경계는 [StructuringHandoffService] 에 있다 — 여기서 열면 실패 처리까지 같은 트랜잭션에
 * 묶여 함께 롤백된다.
 */
@Component
class StructuringHandoffListener(
    private val handoffService: StructuringHandoffService,
    private val ingestionService: IngestionService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async(AsyncConfig.STRUCTURING_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onComputed(computed: StructuringComputed) {
        try {
            handoffService.handoff(computed)
        } catch (e: Exception) {
            // 인계가 통째로 롤백됐으므로 세션은 아직 STRUCTURED 가 아니다 → FAILED 로 남겨 재시도하게 한다
            log.warn("세션 {} 구조화 결과 인계 실패", computed.ingestionId, e)
            ingestionService.markFailed(computed.ingestionId)
        }
    }
}
