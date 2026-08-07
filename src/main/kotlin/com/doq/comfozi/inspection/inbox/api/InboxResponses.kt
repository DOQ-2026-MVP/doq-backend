package com.doq.comfozi.inspection.inbox.api

import com.doq.comfozi.inspection.inbox.domain.Inbox
import com.doq.comfozi.inspection.inbox.domain.InboxItem
import com.doq.comfozi.inspection.inbox.domain.InboxItemStatus
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.mapping.MappedRecord
import java.time.LocalDateTime

/** 인박스 상세 — 항목 포함. */
data class InboxResponse(
    val inboxId: Long,
    val ingestionId: Long,
    val createdAt: LocalDateTime,
    val items: List<InboxItemResponse>,
) {
    constructor(inbox: Inbox, items: List<InboxItemResponse>) : this(
        inboxId = requireNotNull(inbox.id),
        ingestionId = inbox.ingestionId,
        createdAt = inbox.createdAt,
        items = items,
    )
}

/** 검수 항목 — 관찰값(observed)과 편집본(current)을 함께 노출. */
data class InboxItemResponse(
    val id: Long,
    val recordId: Long,
    val uploadType: IngestionUploadType?,
    val rowNo: Int?,
    val status: InboxItemStatus,
    val flags: Set<AnomalyRuleBasedFlag>,
    val observed: MappedRecord,
    val current: MappedRecord,
) {
    constructor(item: InboxItem) : this(
        id = requireNotNull(item.id),
        recordId = item.recordId,
        uploadType = item.uploadType,
        rowNo = item.rowNo,
        status = item.status,
        flags = item.flags,
        observed = item.observed,
        current = item.current,
    )
}
