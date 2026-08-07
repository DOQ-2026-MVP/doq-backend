package com.doq.comfozi.inspection.inbox

import com.doq.comfozi.inspection.inbox.domain.InboxItem
import com.doq.comfozi.inspection.inbox.repository.InboxItemRepository
import com.doq.comfozi.structuring.StructuredRecord
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 구조화 결과([StructuredRecord]) 수신 → [InboxItem] 영속 (inspection).
 *
 * 동기(same-tx) 수신 — 계산은 structuring, 여기선 결과 **저장만** 한다(struct 트랜잭션과 원자적으로 커밋).
 * TODO: 추후 디커플링 고려 (@TransactionalEventListener(AFTER_COMMIT) / outbox).
 */
@Component
class InboxIntakeListener(
    private val inboxItemRepository: InboxItemRepository,
) {

    @EventListener
    fun on(event: StructuredRecord) {
        inboxItemRepository.save(
            InboxItem(
                ingestionId = event.ingestionId,
                recordId = event.recordId,
                uploadType = event.uploadType,
                rowNo = event.rowNo,
                observed = event.observed,
                current = event.observed.copy(), // 편집본은 독립 사본
                flags = event.flags,
            ),
        )
    }
}
