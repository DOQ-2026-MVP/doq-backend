package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadStatus
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import java.time.LocalDateTime

/**
 * 업로드 현황 응답 — 세션에 올라간 파일 한 건.
 *
 * 처리는 업로드 응답 이후 비동기로 돌기 때문에 **실패도 여기로 드러난다** —
 * `status=PARSE_FAILED` 와 [failureReason]. 원본은 지워지지 않으므로 확인 후 삭제·재업로드하면 된다.
 * (원본 문서는 행 추출을 지원하지 않아 행 없이 `PARSED` 가 된다 — 수기 입력으로 보완.)
 */
data class IngestionUploadResponse(
    val id: Long,
    val type: IngestionUploadType,
    val status: IngestionUploadStatus,
    val fileName: String,
    val contentType: String?,
    val size: Long?,
    val failureReason: String?,
    val createdAt: LocalDateTime,
) {
    constructor(upload: IngestionUpload) : this(
        id = requireNotNull(upload.id),
        type = upload.type,
        status = upload.status,
        fileName = upload.fileName,
        contentType = upload.contentType,
        size = upload.size,
        failureReason = upload.failureReason,
        createdAt = upload.createdAt,
    )
}
