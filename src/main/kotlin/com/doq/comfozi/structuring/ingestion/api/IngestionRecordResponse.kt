package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType

/**
 * 인입 원본 행 응답 — 저장된 [IngestionRecord]의 조회 표현. 값은 원문 그대로.
 * [uploadType]/[uploadRowNo]는 파일 출처(BATCH_FILE)일 때만 채워지고, 수기 입력이면 null.
 */
data class IngestionRecordResponse(
    val id: Long,
    val uploadType: IngestionUploadType? = null,
    val uploadRowNo: Int? = null,
    val docId: String? = null,
    val sourceType: String? = null,
    val supplier: String? = null,
    val rawItemName: String? = null,
    val spec: String? = null,
    val unit: String? = null,
    val priceBefore: String? = null,
    val priceAfter: String? = null,
    val effectiveDate: String? = null,
) {
    constructor(record: IngestionRecord) : this(
        id = requireNotNull(record.id),
        uploadType = record.uploadRef?.uploadType,
        uploadRowNo = record.uploadRef?.rowNo,
        docId = record.docId,
        sourceType = record.sourceType,
        supplier = record.supplier,
        rawItemName = record.rawItemName,
        spec = record.spec,
        unit = record.unit,
        priceBefore = record.priceBefore,
        priceAfter = record.priceAfter,
        effectiveDate = record.effectiveDate,
    )
}
