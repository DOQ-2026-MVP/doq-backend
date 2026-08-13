package com.doq.comfozi.structuring

import com.doq.comfozi.ingestion.domain.IngestionStatus
import com.doq.comfozi.ingestion.service.IngestionReadService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 구조화 결과 인계 — 검수 인박스 적재와 세션의 STRUCTURED 전이를 **한 트랜잭션**에서 끝낸다.
 *
 * 둘을 같은 트랜잭션에 두는 것이 이 클래스의 존재 이유다. 나누면 "인박스는 저장됐는데 세션은
 * STRUCTURED 가 아니다"(재시도 시 인박스 중복) 또는 그 반대(검수할 게 없는 완료 세션)가 생긴다.
 * STRUCTURED 는 곧 **"검수 인박스가 준비됐다"** 를 뜻하며, 그 전에 실패하면 세션이 DRAFT/FAILED 로
 * 남아 재시도(`POST /api/structuring/{id}`)로 온전히 복구된다 — 커밋된 게 없으므로 중복도 없다.
 *
 * 적재 자체는 [StructuredRecords] 를 받는 inspection 쪽 리스너가 같은 트랜잭션에서 수행한다
 * (structuring 은 계산하고, inspection 이 저장한다는 경계를 그대로 둔다).
 */
@Service
class StructuringHandoffService(
    private val readService: IngestionReadService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun handoff(computed: StructuringComputed) {
        val session = readService.getSession(computed.ingestionId)

        // 이미 인계된 세션이면 조용히 지나간다 — 중복 이벤트나 동시 요청에서 인박스를 두 번 만들지 않는다
        if (session.status == IngestionStatus.STRUCTURED) return

        eventPublisher.publishEvent(StructuredRecords(computed.ingestionId, computed.records))
        session.markStructured() // 같은 tx, 더티체킹으로 영속
    }
}
