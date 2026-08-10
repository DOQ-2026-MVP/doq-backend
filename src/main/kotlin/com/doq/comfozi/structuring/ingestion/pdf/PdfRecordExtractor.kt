package com.doq.comfozi.structuring.ingestion.pdf

/**
 * PDF 원본 → 구매 증빙 **항목** 추출기 (추가 요건). 한 PDF에서 0건 이상의 항목이 나올 수 있다.
 *
 * 구현은 LLM 등 외부 의존을 감출 뿐, 값은 원문 그대로(캐노니컬 9필드 문자열) 돌려준다 — 검증·정규화는 후속 structuring 소관.
 * 테스트는 이 인터페이스를 페이크로 대체해 실제 API 호출 없이 인입 흐름을 검증한다.
 */
fun interface PdfRecordExtractor {

    /** [fileName]의 PDF 바이트에서 항목들을 추출한다(입력 순서 유지). */
    fun extract(fileName: String, pdfBytes: ByteArray): List<PdfExtractedItem>
}

/**
 * PDF에서 추출한 항목 1건 — 요구사항 입력 9필드(캐노니컬 키). 원문 그대로 담으며 값은 모두 문자열/nullable이다
 * (누락은 null, 숫자·날짜도 변환 없이 원문 문자열). structuring이 이후 매핑·정규화·검증을 수행한다.
 */
data class PdfExtractedItem(
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
