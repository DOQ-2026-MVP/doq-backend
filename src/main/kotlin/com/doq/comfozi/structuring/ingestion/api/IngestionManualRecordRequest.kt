package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.service.IngestionManualInput

/**
 * 수기 입력 요청 바디 — 업로드 없이 원본 행 1건을 세션에 추가한다.
 *
 * 대상 세션(신규/기존)은 엔드포인트로 구분한다: `POST /records`(신규), `POST /records/{ingestionId}`(기존).
 * 9개 데이터 컬럼은 인입 시점엔 검증하지 않으므로 모두 선택값(원문 그대로, 검증/정규화는 후속 structuring).
 */
data class IngestionManualRecordRequest(
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
    /** 서비스 입력 DTO로 매핑. */
    fun toInput(): IngestionManualInput =
        IngestionManualInput(
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
