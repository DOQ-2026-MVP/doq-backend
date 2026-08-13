package com.doq.comfozi.ingestion.api

import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.ingestion.domain.IngestionUploadType
import java.time.LocalDateTime

/**
 * 인입 원본 행 응답 — 저장된 [IngestionRecord]의 조회 표현.
 *
 * [content]는 원문 그대로(매핑 전). 파일 출처면 [uploadId]/[uploadType]/[uploadRowNo]가 채워지고, 수기면 null.
 */
data class IngestionRecordResponse(
    val id: Long,
    val uploadId: Long? = null,
    val uploadType: IngestionUploadType? = null,
    val uploadRowNo: Int? = null,
    val content: Map<String, String?>,
    val createdAt: LocalDateTime,
) {
    constructor(record: IngestionRecord) : this(
        id = requireNotNull(record.id),
        uploadId = record.uploadRef?.uploadId,
        uploadType = record.uploadRef?.uploadType,
        uploadRowNo = record.uploadRef?.rowNo,
        content = record.content.values,
        createdAt = record.createdAt,
    )
}
