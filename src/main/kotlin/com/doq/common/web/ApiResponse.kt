package com.doq.common.web

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 모든 API 컨트롤러가 공유하는 응답 envelope.
 *
 * 성공이면 [data]가, 실패이면 [error]가 채워진다(둘 중 하나만).
 * 컨트롤러는 [ok]/[fail] 팩토리로 감싸고, HTTP 상태 코드는 `ResponseEntity`로 별도 표현한다.
 *
 * 사용 예:
 * ```
 * @GetMapping("/{id}")
 * fun get(@PathVariable id: Long): ResponseEntity<ApiResponse<FooResponse>> =
 *     ResponseEntity.ok(ApiResponse.ok(service.find(id)))
 * ```
 */
@Schema(description = "공통 응답 envelope — 성공 시 data, 실패 시 error 가 채워진다")
data class ApiResponse<T>(
    @get:Schema(description = "요청 성공 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    val success: Boolean,

    @get:Schema(description = "응답 데이터 (성공 시)")
    val data: T? = null,

    @get:Schema(description = "에러 정보 (실패 시)")
    val error: ApiError? = null,
) {
    companion object {
        /** 데이터를 담은 성공 응답. */
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)

        /** 반환할 본문이 없는 성공 응답(예: 삭제). */
        fun ok(): ApiResponse<Unit> = ApiResponse(success = true)

        /** 실패 응답 — [ApiError]로 원인을 표현한다. */
        fun fail(error: ApiError): ApiResponse<Nothing> = ApiResponse(success = false, error = error)

        /** 실패 응답 단축형. */
        fun fail(code: String, message: String): ApiResponse<Nothing> =
            fail(ApiError(code = code, message = message))
    }
}
