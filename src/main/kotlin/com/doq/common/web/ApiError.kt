package com.doq.common.web

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 실패 응답의 에러 본문 — [ApiResponse.error]에 담긴다.
 *
 * [code]는 클라이언트가 분기할 수 있는 안정적인 식별자(예: `INGESTION_NOT_FOUND`),
 * [message]는 사람이 읽는 설명, [fields]는 입력 검증 실패 시 필드별 사유.
 */
@Schema(description = "실패 응답 본문")
data class ApiError(
    @get:Schema(description = "에러 코드 — 클라이언트 분기용 안정 식별자", example = "INGESTION_NOT_FOUND", requiredMode = Schema.RequiredMode.REQUIRED)
    val code: String,

    @get:Schema(description = "사람이 읽는 에러 설명", example = "해당 인입 세션을 찾을 수 없습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
    val message: String,

    @get:Schema(description = "입력 검증 실패 시 필드별 사유 (없으면 생략)")
    val fields: List<FieldError>? = null,
) {
    /** 필드 단위 검증 실패 상세. */
    @Schema(description = "필드 단위 검증 실패 상세")
    data class FieldError(
        @get:Schema(description = "실패한 필드 이름", example = "docId", requiredMode = Schema.RequiredMode.REQUIRED)
        val field: String,

        @get:Schema(description = "실패 사유", example = "필수 값입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        val reason: String,
    )
}
