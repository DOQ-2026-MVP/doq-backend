package com.doq.comfozi.ingestion.api

import com.doq.comfozi.ingestion.domain.Ingestion
import com.doq.comfozi.ingestion.service.IngestionFileInput
import com.doq.comfozi.ingestion.service.IngestionReadService
import com.doq.comfozi.ingestion.service.IngestionService
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
 * 적재는 upsert 다 — 경로에 `{ingestionId}` 가 없으면 새 세션, 있으면 그 세션에 이어붙인다
 * (없거나 DRAFT 가 아니면 서비스가 예외). 파일 업로드는 표 파일·원본 문서 구분 없이 `/uploads`
 * 하나로 받고 처리 경로는 내용으로 판정한다. 값은 원문 그대로 저장하며 검증/정규화는 하지 않는다.
 *
 * 변경 응답은 바뀐 뒤의 [IngestionState] — 현황 스트림이 흘리는 것과 같은 타입이다.
 *
 * 메소드 순서는 [IngestionService]와 맞춘다 — **적재(입력 종류별 create·continue 쌍) → 수정 → 삭제**,
 * 삭제는 대상 범위가 좁은 것부터(행 → 업로드 → 세션 전체).
 */
@Tag(name = "인입(Ingestion)", description = "파일 업로드·수기 입력을 세션에 적재하는 인입 API")
@RestController
@RequestMapping("/api/ingestion")
class IngestionController(
    private val service: IngestionService,
    private val readService: IngestionReadService,
) {

    /**
     * 파일 업로드 적재(upsert). multipart `file` 필수.
     * 경로에 `{ingestionId}` 가 있으면 그 세션에 이어붙이고, 없으면 **새 세션**을 만든다.
     *
     * 취합 표 파일(CSV·XLSX)이면 파싱해 원본 행을 적재하고, 원본 증빙 문서(PDF·PNG·JPEG)면
     * 보관만 한다. **어느 쪽인지는 내용(매직 바이트)으로 판정**하므로 호출부가 미리 구분하지 않는다.
     *
     * 201 은 **접수**를 뜻한다 — 파싱은 응답 이후 비동기로 돌기 때문에 응답의 그 업로드는 아직
     * `PARSING` 이다. 진행·결과는 현황 스트림으로 이어서 온다. 지원하지 않는 파일 형식만 400 으로 거른다.
     */
    @Operation(summary = "파일 업로드 접수 (파싱·추출은 비동기)")
    @PostMapping(
        value = ["/uploads", "/{ingestionId}/uploads"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadFile(
        @PathVariable(required = false) ingestionId: Long?,
        @RequestPart("file") file: MultipartFile,
    ): ApiResponse<IngestionState> {
        val ingestion = service.ingestFile(file.toFileInput(), ingestionId)

        return state(ingestion)
    }

    /**
     * 수기 입력 적재(upsert) — 행은 업로드 출처 없이 생성된다(uploadRef=null).
     * 경로에 `{ingestionId}` 가 있으면 그 세션에 이어붙이고, 없으면 새 세션을 만든다.
     */
    @Operation(summary = "수기 입력 적재 (행 생성)")
    @PostMapping(value = ["/records", "/{ingestionId}/records"])
    @ResponseStatus(HttpStatus.CREATED)
    fun addManualRecords(
        @PathVariable(required = false) ingestionId: Long?,
        @Valid @RequestBody requests: List<@Valid IngestionManualRecordRequest>,
    ): ApiResponse<IngestionState> {
        val inputs = requests.map { it.toInput() }
        val ingestion = service.ingestManual(inputs, ingestionId)

        return state(ingestion)
    }

    /**
     * 수기 행 수정 — 9필드를 **전체 교체**한다(부분 갱신 아님). 검증은 추가 때와 동일.
     * 파일 출처 행은 원본 근거라 대상이 아니다(409) — 구조화 이후 검수 단계에서 수정한다.
     */
    @Operation(summary = "수기 행 수정 (9필드 전체 교체)")
    @PutMapping("/{ingestionId}/records/{ingestionRecordId}")
    fun updateManualRecord(
        @PathVariable ingestionId: Long,
        @PathVariable ingestionRecordId: Long,
        @Valid @RequestBody request: IngestionManualRecordRequest,
    ): ApiResponse<IngestionRecordResponse> {
        val record = service.updateManualRecord(ingestionId, ingestionRecordId, request.toInput())
        return ApiResponse.ok(data = IngestionRecordResponse(record))
    }

    /** 원본 행 1건 삭제 (수기·파일 무관). */
    @Operation(summary = "원본 행 1건 삭제")
    @DeleteMapping("/{ingestionId}/records/{ingestionRecordId}")
    fun deleteRecord(
        @PathVariable ingestionId: Long,
        @PathVariable ingestionRecordId: Long,
    ): ApiResponse<IngestionState> {
        val ingestion = service.deleteRecord(ingestionId, ingestionRecordId)

        return state(ingestion)
    }

    /** 업로드 1건 삭제 — 그 업로드에서 나온 행·저장 원본까지. 다른 업로드의 행과 수기 행은 남는다. */
    @Operation(summary = "업로드 1건 삭제 (해당 업로드의 원본 행·저장 파일 포함)")
    @DeleteMapping("/{ingestionId}/uploads/{uploadId}")
    fun deleteUpload(
        @PathVariable ingestionId: Long,
        @PathVariable uploadId: Long,
    ): ApiResponse<IngestionState> {
        val ingestion = service.deleteUpload(ingestionId, uploadId)

        return state(ingestion)
    }

    /** 세션 비우기(truncate) — 원본 행(수기·파일)·업로드·저장 파일을 모두 제거하고 세션을 DRAFT로 되돌린다(수정·재시도용). */
    @Operation(summary = "세션 비우기 (원본 행·업로드·파일 모두 제거 후 DRAFT로 되돌림)")
    @DeleteMapping("/{ingestionId}/records")
    fun truncate(
        @PathVariable ingestionId: Long,
    ): ApiResponse<IngestionState> {
        val ingestion = service.truncate(ingestionId)

        return state(ingestion)
    }

    /**
     * 바뀐 뒤의 세션 현황 — 현황 스트림이 흘리는 것과 **같은 타입**이라, 화면은 자기 요청의 응답으로
     * 받든 스트림으로 받든 같은 모델을 다룬다. 변경 트랜잭션은 이미 커밋됐으므로 다시 읽어 조립한다.
     */
    private fun state(ingestion: Ingestion): ApiResponse<IngestionState> {
        val status = readService.getStatus(requireNotNull(ingestion.id))

        return ApiResponse.ok(data = IngestionState(status))
    }

    private fun MultipartFile.toFileInput() = IngestionFileInput(
        fileName = originalFilename ?: "unknown",
        contentType = contentType,
        content = inputStream,
    )
}
