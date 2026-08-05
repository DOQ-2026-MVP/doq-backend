package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord

/** 수기 입력 값 — 요구사항 §입력 파일 컬럼(9개), 원문 그대로. */
data class IngestionManualInput(
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

    /**
     * 원본 행 엔티티로 변환. 업로드 출처가 없으므로 [IngestionRecord.uploadRef]는 null.
     * 값은 원문 그대로(검증/정규화는 후속 structuring).
     */
    fun toEntity(ingestionId: Long): IngestionRecord =
        IngestionRecord(
            ingestionId = ingestionId,
            uploadRef = null,
            docId = docId,
            sourceType = sourceType,
            supplier = supplier,
            rawItemName = rawItemName,
            spec = spec,
            unit = unit,
            priceBefore = priceBefore,
            priceAfter = priceAfter,
            effectiveDate = effectiveDate,
        )
}
