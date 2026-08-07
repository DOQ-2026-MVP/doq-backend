package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.service.IngestionBatchFileInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import com.doq.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * 인입 API — Ingestion 세션에 데이터를 넣는 입구.
 *
 * create(세션 신규) / continue(기존 DRAFT 세션에 이어붙임)를 엔드포인트로 분리한다.
 * continue는 대상 세션을 URL path(`{ingestionId}`)로 지정하며, 존재하지 않거나 DRAFT가 아니면 서비스가 예외.
 * 값은 원문 그대로 저장하며 검증/정규화는 하지 않는다(후속 structuring).
 */
@Tag(name = "인입(Ingestion)", description = "파일 업로드·수기 입력을 세션에 적재하는 인입 API")
@RestController
@RequestMapping("/api/ingestion")
class IngestionController(
    private val service: IngestionService,
) {

    /** 취합 파일(CSV/XLSX) 업로드로 **새 세션** 생성 + 원본 행 적재. multipart `file` 필수. */
    @Operation(summary = "취합 파일 업로드로 새 세션 생성 + 원본 행 적재")
    @PostMapping("/uploads", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadBatchFile(
        @RequestPart("file") file: MultipartFile,
    ): ApiResponse<IngestionMutationResponse> {
        val ingestion = service.createFromBatchFile(file.toBatchInput())
        return ApiResponse.ok(data = IngestionMutationResponse(ingestion))
    }

    /** 취합 파일 업로드를 **기존 세션**에 이어붙임. */
    @Operation(summary = "취합 파일 업로드를 기존 세션에 이어붙임")
    @PostMapping("/{ingestionId}/uploads", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun continueBatchFile(
        @PathVariable ingestionId: Long,
        @RequestPart("file") file: MultipartFile,
    ): ApiResponse<IngestionMutationResponse> {
        val ingestion = service.continueFromBatchFile(ingestionId, file.toBatchInput())
        return ApiResponse.ok(data = IngestionMutationResponse(ingestion))
    }

    /** 수기 입력들로 **새 세션** 생성 + 행 적재(uploadRef=null). */
    @Operation(summary = "수기 입력들로 새 세션 생성 + 행 적재")
    @PostMapping("/records")
    @ResponseStatus(HttpStatus.CREATED)
    fun addManualRecords(
        @Valid @RequestBody requests: List<@Valid IngestionManualRecordRequest>,
    ): ApiResponse<IngestionMutationResponse> {
        val ingestion = service.createFromManualRecords(requests.map { it.toInput() })
        return ApiResponse.ok(data = IngestionMutationResponse(ingestion))
    }

    /** 수기 입력들을 **기존 세션**에 이어붙임. */
    @Operation(summary = "수기 입력들을 기존 세션에 이어붙임")
    @PostMapping("/{ingestionId}/records")
    @ResponseStatus(HttpStatus.CREATED)
    fun continueManualRecords(
        @PathVariable ingestionId: Long,
        @Valid @RequestBody requests: List<@Valid IngestionManualRecordRequest>,
    ): ApiResponse<IngestionMutationResponse> {
        val ingestion = service.continueFromManualRecords(ingestionId, requests.map { it.toInput() })
        return ApiResponse.ok(data = IngestionMutationResponse(ingestion))
    }

    /** 세션 비우기(truncate) — 원본 행(수기·파일)·업로드·저장 파일을 모두 제거하고 세션을 DRAFT로 되돌린다(수정·재시도용). */
    @Operation(summary = "세션 비우기 (원본 행·업로드·파일 모두 제거 후 DRAFT로 되돌림)")
    @DeleteMapping("/{ingestionId}/records")
    fun truncate(
        @PathVariable ingestionId: Long,
    ): ApiResponse<IngestionMutationResponse> {
        val ingestion = service.truncate(ingestionId)
        return ApiResponse.ok(data = IngestionMutationResponse(ingestion))
    }

    private fun MultipartFile.toBatchInput() = IngestionBatchFileInput(
        fileName = originalFilename ?: "unknown",
        contentType = contentType,
        content = inputStream,
    )
}
