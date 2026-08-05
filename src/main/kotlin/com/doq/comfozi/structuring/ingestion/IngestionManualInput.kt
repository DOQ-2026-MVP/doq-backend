package com.doq.comfozi.structuring.ingestion

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
)
