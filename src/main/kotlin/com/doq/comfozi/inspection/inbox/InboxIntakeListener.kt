package com.doq.comfozi.inspection.inbox

import com.doq.comfozi.structuring.StructuredRecord
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 구조화 결과([StructuredRecord]) 수신 → InboxItem 영속 (inspection).
 *
 * 동기(same-tx) 수신 — 계산은 structuring, 여기선 결과 **저장만** 한다.
 * TODO: 추후 디커플링 고려 (@TransactionalEventListener(AFTER_COMMIT) / outbox).
 * TODO: InboxItem(관찰값 + status=new + 플래그 + sourceRef) 생성·영속 — 지금은 println 스텁.
 */
@Component
class InboxIntakeListener {

    @EventListener
    fun on(event: StructuredRecord) {
        println("[InboxIntake] recordId=${event.recordId} flags=${event.flags} observed=${event.observed}")
    }
}
