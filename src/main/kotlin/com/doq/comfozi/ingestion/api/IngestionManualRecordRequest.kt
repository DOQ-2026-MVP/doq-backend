package com.doq.comfozi.ingestion.api

import com.doq.comfozi.ingestion.domain.SourceType
import com.doq.comfozi.ingestion.service.IngestionManualInput
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.PositiveOrZero
import java.time.LocalDate

/**
 * 수기 입력 요청 바디 — 업로드 없이 원본 행 1건을 세션에 추가한다.
 *
 * 수기는 **정해진 형식**이라 인입 시점에 전 필드를 검증한다(틀리면 즉시 400, 프론트가 바로 고치도록).
 * 단가·적용일은 자료형(숫자/`LocalDate`)으로 형식이 보장되고(잘못된 형식은 파싱 단계 400),
 * 존재는 `@NotNull`/`@NotBlank`로 강제한다. 값 자체의 이상은 후속 detection이 플래그로 surface.
 *
 * 필드가 nullable인 건 "누락"을 어노테이션이 잡아 **필드별 메시지**를 주기 위함이다(계약은 어노테이션이 짐).
 */
data class IngestionManualRecordRequest(
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
) {
    /** 서비스 입력 DTO로 매핑. @Valid 통과 후 호출되므로 전 필드 non-null 보장(`!!`). */
    fun toInput(): IngestionManualInput =
        IngestionManualInput(
            docId = docId!!,
            sourceType = SourceType.normalizeOrKeep(sourceType)!!,
            supplier = supplier!!,
            rawItemName = rawItemName!!,
            spec = spec!!,
            unit = unit!!,
            priceBefore = priceBefore!!,
            priceAfter = priceAfter!!,
            effectiveDate = effectiveDate!!,
        )
}
