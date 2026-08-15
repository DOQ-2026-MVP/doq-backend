package com.doq.comfozi.inspection.api

import com.doq.comfozi.ingestion.domain.SourceType
import com.doq.comfozi.structuring.mapping.MappedRecord
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * 검수 레코드 편집 요청 — 편집본([MappedRecord])과 메모 전체를 교체한다(관찰값 observed는 불변).
 *
 * 캐노니컬 9필드 + 정규화 품목명 + 메모를 실어 보낸다. 값을 비우면(null) 해당 필드가 비워진다(부분 패치 아님, 전체 교체).
 */
data class InspectionRecordEditRequest(
    @field:NotBlank(message = "문서ID는 필수입니다")
    val docId: String? = null,

    @field:NotBlank(message = "원본유형은 필수입니다")
    val sourceType: String? = null,

    @field:NotBlank(message = "공급사는 필수입니다")
    val supplier: String? = null,

    @field:NotBlank(message = "원문품목명은 필수입니다")
    val rawItemName: String? = null,

    @field:NotBlank(message = "규격은 필수입니다")
    val spec: String? = null,

    @field:NotBlank(message = "단위는 필수입니다")
    val unit: String? = null,

    @field:NotNull(message = "기존단가는 필수입니다")
    @field:PositiveOrZero(message = "금액은 음수일 수 없습니다")
    val priceBefore: Long? = null,

    @field:NotNull(message = "변경단가는 필수입니다")
    @field:PositiveOrZero(message = "금액은 음수일 수 없습니다")
    val priceAfter: Long? = null,

    @field:NotNull(message = "적용일은 필수입니다 (yyyy-MM-dd)")
    @field:PastOrPresent(message = "적용일은 미래일 수 없습니다.")
    val effectiveDate: LocalDate? = null,

    val normalizedItemName: String? = null,

    /** 검수 메모 — 검수자 코멘트. 값과 함께 전체 교체되므로, 유지하려면 기존 메모를 그대로 실어 보낸다. */
    @field:Size(max = 1000, message = "메모는 1000자를 넘을 수 없습니다")
    val memo: String? = null,
) {
    fun toMappedRecord() = MappedRecord(
        docId = docId,
        // 검수로 들어오는 값은 어휘로 맞춰 받는다 — 클라이언트가 확장자 표기를 되돌려 보내도
        // 같은 뜻이 PNG·IMAGE 로 갈라져 쌓이지 않는다. 모르는 값은 원문 그대로 둔다.
        sourceType = SourceType.normalizeOrKeep(sourceType),
        supplier = supplier,
        rawItemName = rawItemName,
        spec = spec,
        unit = unit,
        // 빈 값은 빈 값으로 — `Any?.toString()` 은 null 을 "null" 이라는 네 글자 문자열로 만든다.
        // 그렇게 담기면 값이 비었는데도 필수값 누락(missing_required)에 걸리지 않는다.
        priceBefore = priceBefore?.toString(),
        priceAfter = priceAfter?.toString(),
        effectiveDate = effectiveDate?.toString(),
        normalizedItemName = normalizedItemName,
    )
}
