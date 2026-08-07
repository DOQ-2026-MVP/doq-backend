package com.doq.comfozi.structuring.api

import com.doq.comfozi.structuring.StructuringService
import com.doq.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 구조화 API — 인입 세션을 구조화(매핑·정규화·탐지)해 검수 인박스로 인계하는 트리거.
 *
 * 존재하지 않는 세션이면 서비스가 [NoSuchElementException] → 404.
 * (상태 전이 STRUCTURED/FAILED·검증 선행·재시도 배선은 후속.)
 */
@Tag(name = "구조화(Structuring)", description = "인입 세션을 구조화해 검수 인박스로 인계하는 API")
@RestController
@RequestMapping("/api/structuring")
class StructuringController(
    private val service: StructuringService,
) {

    /** [ingestionId] 세션 구조화 실행 — 레코드별 매핑·정규화·탐지 후 inspection으로 인계. */
    @Operation(summary = "인입 세션 구조화 실행")
    @PostMapping("/{ingestionId}")
    fun struct(
        @PathVariable ingestionId: Long,
    ): ApiResponse<Unit> {
        service.struct(ingestionId)
        return ApiResponse.ok()
    }
}
