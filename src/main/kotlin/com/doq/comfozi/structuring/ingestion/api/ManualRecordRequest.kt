package com.doq.comfozi.structuring.ingestion.api

/**
 * 수기 입력 요청 바디 — 업로드 없이 원본 행 1건을 세션에 추가한다.
 *
 * [ingestionId]가 있으면 그 DRAFT 세션에 붙이고, 없으면 새 세션을 열어 첫 행을 넣는다
 * (요구사항: 입력이 한 번이라도 있으면 세션 생성).
 * 9개 데이터 컬럼은 인입 시점엔 검증하지 않으므로 모두 선택값(원문 그대로, 검증/정규화는 후속 structuring).
 *
 * NOTE: 서비스 계약(doq-backend `IngestionService.ManualRecordInput`)의 9개 필드와 1:1 대응.
 * 병합 후 컨트롤러에서 이 요청을 `ManualRecordInput`으로 매핑한다.
 */
data class ManualRecordRequest(
    val ingestionId: Long? = null,

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
