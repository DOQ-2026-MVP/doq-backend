package com.doq.common.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

/**
 * 전역 예외 → [ApiResponse.fail] envelope 매핑. 컨트롤러가 던진 도메인 예외를 일관된 실패 응답으로 변환한다.
 *
 * 여기서 다루지 않는 요청 바인딩 오류(누락 파트·JSON 파싱 등)는 Spring 기본 처리에 맡긴다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /** 대상 리소스 없음 — 예: 존재하지 않는 세션. */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException) =
        fail(HttpStatus.NOT_FOUND, "NOT_FOUND", e.message)

    /** 현재 상태에서 허용되지 않는 요청 — 예: 확정된(비-DRAFT) 세션에 추가. */
    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(e: IllegalStateException) =
        fail(HttpStatus.CONFLICT, "CONFLICT", e.message)

    /** 잘못된 요청 인자. */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException) =
        fail(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.message)

    /** 요청 본문 형식 오류 — 예: 깨진 JSON. (원문 메시지는 내부 정보라 노출하지 않음) */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException) =
        fail(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "요청 본문 형식이 올바르지 않습니다")

    /** 단일 바디 검증 실패(@Valid) — 위반을 필드별로 쪼개 [ApiError.fields]에 싣는다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalid(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> =
        invalid(e.bindingResult.fieldErrors.map { fieldError(it.field, it.defaultMessage) })

    /** 메서드 파라미터/리스트 원소 검증 실패 — 예: `List<@Valid …>` 수기 입력. (@Valid 단일 바디와 예외 타입이 다름) */
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(e: HandlerMethodValidationException): ResponseEntity<ApiResponse<Nothing>> {
        // 리스트 바디는 원소마다 BindingResult 가 따로 나온다 — 몇 번째 행인지 알아야 프론트가 그 행을 짚는다.
        val fields = e.beanResults.flatMap { result ->
            val prefix = result.containerIndex?.let { "[$it]." } ?: ""
            result.fieldErrors.map { fieldError(prefix + it.field, it.defaultMessage) }
        }
        // 필드에 매이지 않은 위반(파라미터 자체 제약)은 필드명이 없으므로 메시지만 남긴다.
        return if (fields.isEmpty()) {
            invalidMessage(e.allErrors.mapNotNull { it.defaultMessage }.joinToString(", "))
        } else {
            invalid(fields)
        }
    }

    private fun fieldError(field: String, message: String?) =
        ApiError.FieldError(field = field, reason = message ?: "올바르지 않은 값입니다")

    /** 필드별 사유는 [ApiError.fields]로, [ApiError.message]는 사람이 읽는 요약으로만 쓴다. */
    private fun invalid(fields: List<ApiError.FieldError>): ResponseEntity<ApiResponse<Nothing>> {
        if (fields.isEmpty()) return invalidMessage("")
        val summary = if (fields.size == 1) fields[0].reason else "${fields[0].reason} 외 ${fields.size - 1}건"
        return ResponseEntity.badRequest()
            .body(ApiResponse.fail(ApiError(code = "BAD_REQUEST", message = summary, fields = fields)))
    }

    private fun invalidMessage(message: String) =
        fail(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message.ifBlank { "요청 검증에 실패했습니다" })

    private fun fail(status: HttpStatus, code: String, message: String?): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(status).body(ApiResponse.fail(code, message ?: status.reasonPhrase))
}
