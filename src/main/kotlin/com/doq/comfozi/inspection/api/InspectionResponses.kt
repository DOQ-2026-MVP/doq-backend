package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.domain.FieldChange
import com.doq.comfozi.inspection.domain.Inspection
import com.doq.comfozi.inspection.domain.InspectionChangeLog
import com.doq.comfozi.inspection.domain.InspectionChangeType
import com.doq.comfozi.inspection.domain.InspectionRecord
import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.mapping.MappedRecord
import java.time.LocalDateTime

/** 검수 상세 — 레코드 포함. */
data class InspectionResponse(
    val inspectionId: Long,
    val ingestionId: Long,
    val createdAt: LocalDateTime,
    val records: List<InspectionRecordResponse>,
) {
    constructor(inspection: Inspection, records: List<InspectionRecordResponse>) : this(
        inspectionId = requireNotNull(inspection.id),
        ingestionId = inspection.ingestionId,
        createdAt = inspection.createdAt,
        records = records,
    )
}

/**
 * 일괄 확정 결과 — 이번 호출로 확정(NEW→CONFIRMED)된 건수와, 필수값 누락으로 확정하지 못하고
 * 건너뛴(blocked) 건수.
 */
data class InspectionBulkConfirmResponse(
    val inspectionId: Long,
    val confirmedCount: Int,
    val blockedCount: Int,
)

/** 검수 변경 이력 1건 — 편집/전이의 시각·상태·메모와 변경분(diff). */
data class InspectionChangeLogResponse(
    val id: Long,
    val recordId: Long,
    val type: InspectionChangeType,
    val fromStatus: InspectionRecordStatus?,
    val toStatus: InspectionRecordStatus?,
    val memo: String?,
    val changes: List<FieldChange>,
    val createdAt: LocalDateTime,
) {
    constructor(log: InspectionChangeLog) : this(
        id = requireNotNull(log.id),
        recordId = log.recordId,
        type = log.type,
        fromStatus = log.fromStatus,
        toStatus = log.toStatus,
        memo = log.memo,
        changes = log.changes,
        createdAt = log.createdAt,
    )
}

/** 검수 레코드 — 관찰값(observed)과 편집본(current)을 함께 노출. */
data class InspectionRecordResponse(
    val id: Long,
    val recordId: Long,
    val uploadType: IngestionUploadType?,
    val rowNo: Int?,
    val status: InspectionRecordStatus,
    val flags: Set<AnomalyRuleBasedFlag>,
    val observed: MappedRecord,
    val current: MappedRecord,
) {
    constructor(record: InspectionRecord) : this(
        id = requireNotNull(record.id),
        recordId = record.recordId,
        uploadType = record.uploadType,
        rowNo = record.rowNo,
        status = record.status,
        flags = record.flags,
        observed = record.observed,
        current = record.current,
    )
}
