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
 * 인입 조회 API — 세션·업로드 현황·원본 행을 읽는다. (적재는 [IngestionController])
 */
@Tag(name = "인입(Ingestion)", description = "파일 업로드·수기 입력을 세션에 적재하는 인입 API")
@RestController
@RequestMapping("/api/ingestion")
class IngestionReadController(
    private val readService: IngestionReadService,
) {

    /** 인입 세션 + 업로드 현황 + 원본 행 조회. 없으면 404. */
    @Operation(summary = "인입 세션 + 업로드 현황 + 원본 행 조회")
    @GetMapping("/{ingestionId}")
    fun getIngestion(
        @PathVariable ingestionId: Long,
    ): ApiResponse<IngestionMutationResponse> {
        val session = readService.getSession(ingestionId)
        val records = readService.getRecords(ingestionId)

        // 업로드별 행 수는 이미 읽은 행들로 계산 — 추가 쿼리 없음
        val countByUploadId = records.mapNotNull { it.uploadRef?.uploadId }.groupingBy { it }.eachCount()
        val uploads = readService.getUploads(ingestionId)
            .map { IngestionUploadResponse(it, recordCount = countByUploadId[it.id] ?: 0) }

        return ApiResponse.ok(
            data = IngestionMutationResponse(
                ingestion = session,
                uploads = uploads,
                records = records.map(::IngestionRecordResponse),
            ),
        )
    }
}
