package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadStatus
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import java.time.LocalDateTime

/**
 * 업로드 현황 응답 — 세션에 올라간 파일 한 건의 상태.
 *
 * [recordCount]는 이 업로드에서 나온 원본 행 수다. 원본 문서(FILE)는 아직 행을 추출하지 않으므로 0.
 * 파싱에 실패한 업로드는 애초에 저장되지 않아(parse-before-persist) 이 목록에 나타나지 않는다.
 */
data class IngestionUploadResponse(
    val id: Long,
    val type: IngestionUploadType,
    val status: IngestionUploadStatus,
    val fileName: String,
    val contentType: String?,
    val size: Long?,
    val recordCount: Int,
    val createdAt: LocalDateTime,
) {
    constructor(upload: IngestionUpload, recordCount: Int) : this(
        id = requireNotNull(upload.id),
        type = upload.type,
        status = upload.status,
        fileName = upload.fileName,
        contentType = upload.contentType,
        size = upload.size,
        recordCount = recordCount,
        createdAt = upload.createdAt,
    )
}
