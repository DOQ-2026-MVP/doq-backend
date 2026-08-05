package com.doq.comfozi.structuring.ingestion.api

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 인입 API — [com.doq.comfozi.structuring.ingestion.Ingestion] 세션에 데이터를 넣는 입구.
 *
 * 세션은 명시적으로 열지 않는다 — 파일 업로드/수기 입력이 처음 들어오면 세션이 생긴다.
 *
 * NOTE: 서비스(`IngestionService`)는 인접 세션(doq-backend)에서 작성 중이라 이 브랜치엔 아직 없다.
 * 여기(API 계층)는 엔드포인트/DTO만 확정하고, 실제 배선은 병합 후 채운다([TODO]).
 * 병합 시 참고할 서비스 계약:
 *   - `createFromBatchFile(fileName, contentType, content): Ingestion`
 *   - `createSession(): Ingestion`
 *   - `addManualRecord(ingestionId: Long, ManualRecordInput): IngestionRecord`
 */
@RestController
@RequestMapping("/api/ingestion")
class IngestionController {

    /**
     * 취합 파일 업로드 — CSV(BATCH_FILE) 1개를 올려 **새 세션**을 만들고 원본 행들을 적재한다.
     *
     * `multipart/form-data`: `file`(필수). 값 검증 없이 원문 그대로 저장(후속 structuring).
     * NOTE: 원본 문서 1개(FILE: PDF·이미지 등) 업로드 경로는 서비스에 아직 없어 미지원.
     */
    @PostMapping("/uploads", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]) // POST /api/ingestion/uploads
    fun uploadBatchFile(
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<IngestionMutationResponse> {
        // 병합 후: service.createFromBatchFile(file.originalFilename ?: "unknown", file.contentType, file.inputStream)
        //          → ResponseEntity.status(CREATED).body(IngestionMutationResponse(ingestion.id!!, status = ingestion.status))
        TODO("IngestionService 병합 후 배선 — createFromBatchFile")
    }

    /**
     * 수기 입력 — 업로드 없이 원본 행 1건을 추가한다([IngestionRecord.uploadRef]=null).
     *
     * `ingestionId`가 있으면 그 DRAFT 세션에 붙이고, 없으면 새 세션을 열어 첫 행을 넣는다.
     * 9개 데이터 컬럼은 원문 그대로 보관(검증 없음).
     */
    @PostMapping("/records")
    fun addManualRecord(
        @RequestBody request: ManualRecordRequest,
    ): ResponseEntity<IngestionMutationResponse> {
        // 병합 후: val ingestionId = request.ingestionId ?: service.createSession().id!!
        //          val record = service.addManualRecord(ingestionId, request → ManualRecordInput)
        //          → ResponseEntity.status(CREATED).body(IngestionMutationResponse(record.ingestionId, recordId = record.id!!))
        TODO("IngestionService 병합 후 배선 — createSession/addManualRecord")
    }
}
