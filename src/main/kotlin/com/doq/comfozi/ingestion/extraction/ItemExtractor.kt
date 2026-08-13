package com.doq.comfozi.ingestion.extraction

/**
 * 문서 텍스트 → 구매 증빙 **항목** 추출기. 한 문서에서 0건 이상이 나올 수 있다.
 *
 * 구현이 LLM 등 외부 의존을 감춘다 — 테스트는 이 인터페이스를 페이크로 대체해 실제 호출 없이
 * 인입 흐름을 검증한다. 값은 원문 그대로 돌려주며 검증·정규화는 후속 structuring 소관이다.
 */
fun interface ItemExtractor {

    /** [fileName] 문서의 [text] 에서 항목들을 추출한다 (문서에 나온 순서 유지). */
    fun extract(fileName: String, text: String): List<ExtractedItem>
}

/**
 * 추출한 항목 1건 — 문서 본문에서 **읽을 수 있는 것만** 담는다. 값은 모두 문자열/nullable 이고
 * 숫자·날짜도 변환하지 않는다(누락은 null).
 *
 * 필수 9필드 중 `docId`·`sourceType` 이 없는 것은 의도적이다 — 공문 본문에는 그 둘이 없어서
 * **시스템이 부여**해야 한다. 여기서 모델에게 뽑으라고 시키면 없는 값을 지어내거나 전부 null 이
 * 되어, 추출한 모든 항목이 `missing_required` 로 떨어진다.
 */
data class ExtractedItem(
    val supplier: String? = null,
    val rawItemName: String? = null,
    val spec: String? = null,
    val unit: String? = null,
    val priceBefore: String? = null,
    val priceAfter: String? = null,
    val effectiveDate: String? = null,
)
