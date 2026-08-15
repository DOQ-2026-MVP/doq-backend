package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.domain.FieldChange
import com.doq.comfozi.inspection.domain.Inspection
import com.doq.comfozi.inspection.domain.InspectionChangeLog
import com.doq.comfozi.inspection.domain.InspectionChangeType
import com.doq.comfozi.inspection.domain.InspectionRecord
import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.doq.comfozi.ingestion.domain.IngestionUploadType
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
    /** 이력이 붙는 검수 레코드 id — [InspectionRecordResponse.id]와 같은 값. */
    val inspectionRecordId: Long,
    val type: InspectionChangeType,
    val fromStatus: InspectionRecordStatus?,
    val toStatus: InspectionRecordStatus?,
    val changes: List<FieldChange>,
    val createdAt: LocalDateTime,
) {
    constructor(log: InspectionChangeLog) : this(
        id = requireNotNull(log.id),
        inspectionRecordId = log.inspectionRecordId,
        type = log.type,
        fromStatus = log.fromStatus,
        toStatus = log.toStatus,
        changes = log.changes,
        createdAt = log.createdAt,
    )
}

/**
 * 검수 레코드 — 관찰값(observed)과 편집본(current)을 함께 노출.
 *
 * [id]가 이 레코드의 PK로, 레코드 단위 검수 API(`/api/inspection/records/{inspectionRecordId}`)에 넘길 값이다.
 * [ingestionRecordId]는 이 레코드가 나온 **인입 원본 행**의 id로, 출처 추적용일 뿐 검수 API에 넘기면 안 된다.
 */
data class InspectionRecordResponse(
    val id: Long,
    val ingestionRecordId: Long,
    val uploadType: IngestionUploadType?,
    val rowNo: Int?,
    val status: InspectionRecordStatus,
    val memo: String?,
    val flags: Set<AnomalyRuleBasedFlag>,
    val observed: MappedRecord,
    val current: MappedRecord,
) {
    constructor(record: InspectionRecord) : this(
        id = requireNotNull(record.id),
        ingestionRecordId = record.ingestionRecordId,
        uploadType = record.uploadType,
        rowNo = record.rowNo,
        status = record.status,
        memo = record.memo,
        flags = record.flags,
        observed = record.observed,
        current = record.current,
    )
}
