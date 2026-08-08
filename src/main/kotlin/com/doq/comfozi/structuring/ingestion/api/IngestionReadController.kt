package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.service.IngestionReadService
import com.doq.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 인입 조회 API — 세션·원본 행을 읽는다. (적재는 [IngestionController])
 */
@Tag(name = "인입(Ingestion)", description = "파일 업로드·수기 입력을 세션에 적재하는 인입 API")
@RestController
@RequestMapping("/api/ingestion")
class IngestionReadController(
    private val readService: IngestionReadService,
) {

    /** 인입 세션 + 원본 행 조회. 없으면 404. */
    @Operation(summary = "인입 세션 + 원본 행 조회")
    @GetMapping("/{ingestionId}")
    fun getIngestion(
        @PathVariable ingestionId: Long,
    ): ApiResponse<IngestionMutationResponse> {
        val session = readService.getSession(ingestionId)
        val records = readService.getRecords(ingestionId).map(::IngestionRecordResponse)
        return ApiResponse.ok(data = IngestionMutationResponse(session, records))
    }
}
