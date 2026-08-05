package com.doq.common.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

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

    private fun fail(status: HttpStatus, code: String, message: String?): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(status).body(ApiResponse.fail(code, message ?: status.reasonPhrase))
}
