package com.doq.comfozi.inspection.api

import com.doq.comfozi.structuring.mapping.MappedRecord

/** 확정/반려 요청 — 선택적 메모(사유)를 싣는다. 본문 없이 호출해도 된다. */
data class InspectionActionRequest(
    val memo: String? = null,
)

/**
 * 검수 레코드 편집 요청 — 편집본([MappedRecord]) 전체를 교체한다(관찰값 observed는 불변).
 *
 * 캐노니컬 9필드 + 정규화 품목명을 실어 보낸다. 값을 비우면(null) 해당 필드가 비워진다(부분 패치 아님, 전체 교체).
 */
data class InspectionRecordEditRequest(
    val docId: String? = null,
    val sourceType: String? = null,
    val supplier: String? = null,
    val rawItemName: String? = null,
    val spec: String? = null,
    val unit: String? = null,
    val priceBefore: String? = null,
    val priceAfter: String? = null,
    val effectiveDate: String? = null,
    val normalizedItemName: String? = null,
) {
    fun toMappedRecord() = MappedRecord(
        docId = docId,
        sourceType = sourceType,
        supplier = supplier,
        rawItemName = rawItemName,
        spec = spec,
        unit = unit,
        priceBefore = priceBefore,
        priceAfter = priceAfter,
        effectiveDate = effectiveDate,
        normalizedItemName = normalizedItemName,
    )
}
