package com.doq.comfozi.inspection.domain

import com.doq.comfozi.structuring.mapping.MappedRecord

/** 편집으로 바뀐 필드 1개 — 이전값→이후값. (전체 스냅샷이 아니라 변경분만 이력에 남긴다) */
data class FieldChange(
    val field: String,
    val before: String?,
    val after: String?,
)

/** 두 편집본의 필드 단위 diff — 값이 달라진 필드만 반환한다(순서는 캐노니컬 필드 순서). */
fun diffFields(before: MappedRecord, after: MappedRecord): List<FieldChange> =
    listOf(
        FieldChange("docId", before.docId, after.docId),
        FieldChange("sourceType", before.sourceType, after.sourceType),
        FieldChange("supplier", before.supplier, after.supplier),
        FieldChange("rawItemName", before.rawItemName, after.rawItemName),
        FieldChange("spec", before.spec, after.spec),
        FieldChange("unit", before.unit, after.unit),
        FieldChange("priceBefore", before.priceBefore, after.priceBefore),
        FieldChange("priceAfter", before.priceAfter, after.priceAfter),
        FieldChange("effectiveDate", before.effectiveDate, after.effectiveDate),
        FieldChange("normalizedItemName", before.normalizedItemName, after.normalizedItemName),
    ).filter { it.before != it.after }
