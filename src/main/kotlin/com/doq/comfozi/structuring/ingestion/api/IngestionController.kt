package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.service.IngestionBatchFileInput
import com.doq.comfozi.structuring.ingestion.service.IngestionDocumentInput
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
 *
 * 메소드 순서는 [IngestionService]와 맞춘다 — **적재(입력 종류별 create·continue 쌍) → 수정 → 삭제**,
 * 삭제는 대상 범위가 좁은 것부터(행 → 업로드 → 세션 전체).
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

    /**
     * 원본 문서(PDF·이미지) 업로드로 **새 세션** 생성 + 원본 보관. multipart `file` 필수.
     *
     * 취합 표 파일(`/uploads`)과 엔드포인트를 나눈 이유: 처리가 다르다(한쪽은 행을 만들고 한쪽은 보관만).
     * content-type 으로 암묵 분기하면 어느 쪽으로 처리됐는지 호출부가 알 수 없다.
     */
    @Operation(summary = "원본 문서(PDF·이미지) 업로드로 새 세션 생성 + 원본 보관 (행 추출은 미지원)")
    @PostMapping("/documents", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadDocument(
        @RequestPart("file") file: MultipartFile,
    ): ApiResponse<IngestionMutationResponse> {
        val ingestion = service.createFromDocument(file.toDocumentInput())
        return ApiResponse.ok(data = IngestionMutationResponse(ingestion))
    }

    /** 원본 문서 업로드를 **기존 세션**에 이어붙임(보관만). */
    @Operation(summary = "원본 문서(PDF·이미지) 업로드를 기존 세션에 이어붙임")
    @PostMapping("/{ingestionId}/documents", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun continueDocument(
        @PathVariable ingestionId: Long,
        @RequestPart("file") file: MultipartFile,
    ): ApiResponse<IngestionMutationResponse> {
        val ingestion = service.continueFromDocument(ingestionId, file.toDocumentInput())
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

    /**
     * 수기 행 수정 — 9필드를 **전체 교체**한다(부분 갱신 아님). 검증은 추가 때와 동일.
     * 파일 출처 행은 원본 근거라 대상이 아니다(409) — 구조화 이후 검수 단계에서 수정한다.
     */
    @Operation(summary = "수기 행 수정 (9필드 전체 교체)")
    @PutMapping("/{ingestionId}/records/{recordId}")
    fun updateManualRecord(
        @PathVariable ingestionId: Long,
        @PathVariable recordId: Long,
        @Valid @RequestBody request: IngestionManualRecordRequest,
    ): ApiResponse<IngestionRecordResponse> {
        val record = service.updateManualRecord(ingestionId, recordId, request.toInput())
        return ApiResponse.ok(data = IngestionRecordResponse(record))
    }

    /** 원본 행 1건 삭제 (수기·파일 무관). */
    @Operation(summary = "원본 행 1건 삭제")
    @DeleteMapping("/{ingestionId}/records/{recordId}")
    fun deleteRecord(
        @PathVariable ingestionId: Long,
        @PathVariable recordId: Long,
    ): ApiResponse<IngestionMutationResponse> {
        val ingestion = service.deleteRecord(ingestionId, recordId)
        return ApiResponse.ok(data = IngestionMutationResponse(ingestion))
    }

    /** 업로드 1건 삭제 — 그 업로드에서 나온 행·저장 원본까지. 다른 업로드의 행과 수기 행은 남는다. */
    @Operation(summary = "업로드 1건 삭제 (해당 업로드의 원본 행·저장 파일 포함)")
    @DeleteMapping("/{ingestionId}/uploads/{uploadId}")
    fun deleteUpload(
        @PathVariable ingestionId: Long,
        @PathVariable uploadId: Long,
    ): ApiResponse<IngestionMutationResponse> {
        val ingestion = service.deleteUpload(ingestionId, uploadId)
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

    private fun MultipartFile.toDocumentInput() = IngestionDocumentInput(
        fileName = originalFilename ?: "unknown",
        contentType = contentType,
        content = inputStream,
    )
}
