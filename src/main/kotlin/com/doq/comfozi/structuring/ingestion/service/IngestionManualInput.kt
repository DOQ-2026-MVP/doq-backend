package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.IngestionContent
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
     * 입력 9필드를 [IngestionContent]에 **원문 그대로** 담는다(매핑은 후속 structuring).
     */
    fun toEntity(ingestionId: Long): IngestionRecord =
        IngestionRecord(
            ingestionId = ingestionId,
            uploadRef = null,
            content = IngestionContent(
                linkedMapOf(
                    "docId" to docId,
                    "sourceType" to sourceType,
                    "supplier" to supplier,
                    "rawItemName" to rawItemName,
                    "spec" to spec,
                    "unit" to unit,
                    "priceBefore" to priceBefore,
                    "priceAfter" to priceAfter,
                    "effectiveDate" to effectiveDate,
                ),
            ),
        )
}
