package com.doq.comfozi.structuring.mapping

/**
 * 구조화된 레코드(관찰값) — 원문을 캐노니컬 필드로 정렬한 결과.
 *
 * 출처별 [RecordMapper]가 원문을 이 9필드로 옮겨 만들고, 이후 정규화가 [normalizedItemName]을 채운다.
 * detection·검수의 입력. 관찰값은 원문 그대로(불변), 정규화 품목명만 후속 단계에서 세팅한다.
 */
data class MappedRecord(
    val docId: String? = null,
    val sourceType: String? = null,
    val supplier: String? = null,
    val rawItemName: String? = null,
    val spec: String? = null,
    val unit: String? = null,
    val priceBefore: String? = null,
    val priceAfter: String? = null,
    val effectiveDate: String? = null,
    var normalizedItemName: String? = null,
) {
    /** 필수 9필드 값 (정규화 품목명 제외) — 누락 검사용. */
    fun requiredValues(): List<String?> =
        listOf(docId, sourceType, supplier, rawItemName, spec, unit, priceBefore, priceAfter, effectiveDate)

    /** 중복키 구성 값 (요구사항 §2) — 공급사·정규화품목명·규격·단위·기존/변경단가·적용일. */
    fun duplicateKeyValues(): List<String?> =
        listOf(supplier, normalizedItemName, spec, unit, priceBefore, priceAfter, effectiveDate)
}
