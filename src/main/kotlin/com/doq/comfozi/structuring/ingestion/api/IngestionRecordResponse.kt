package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType

/**
 * 인입 원본 행 응답 — 저장된 [IngestionRecord]의 조회 표현.
 *
 * [content]는 원문 그대로(매핑 전). 파일 출처면 [uploadType]/[uploadRowNo]가 채워지고, 수기면 null.
 */
data class IngestionRecordResponse(
    val id: Long,
    val uploadType: IngestionUploadType? = null,
    val uploadRowNo: Int? = null,
    val content: Map<String, String?>,
) {
    constructor(record: IngestionRecord) : this(
        id = requireNotNull(record.id),
        uploadType = record.uploadRef?.uploadType,
        uploadRowNo = record.uploadRef?.rowNo,
        content = record.content.values,
    )
}
