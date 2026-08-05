package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.service.IngestionReadService
import com.doq.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * 인입 조회 API — 세션·원본 행을 읽는다. (적재는 [IngestionController])
 */
@Tag(name = "인입(Ingestion)", description = "파일 업로드·수기 입력을 세션에 적재하는 인입 API")
@RestController
@RequestMapping("/api/ingestion")
class IngestionReadController(
    private val readService: IngestionReadService,
    private val sseHub: IngestionSseHub,
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

    /**
     * 인입 세션의 원본 행 **라이브** SSE 스트림 — 이후 추가되는 행을 `record` 이벤트로 push한다.
     * 초기 스냅샷은 `GET /{ingestionId}`로 받고, 이 스트림으로 델타를 구독한다. 없는 세션이면 404.
     */
    @Operation(summary = "인입 세션의 원본 행 라이브 SSE 스트림")
    @GetMapping("/{ingestionId}/records/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamRecords(
        @PathVariable ingestionId: Long,
    ): SseEmitter {
        readService.getSession(ingestionId) // 존재 확인 (없으면 404)
        return sseHub.subscribe(ingestionId, STREAM_TIMEOUT_MS)
    }

    private companion object {
        const val STREAM_TIMEOUT_MS = 60_000L // SSE 스트림 타임아웃(60s)
    }
}
