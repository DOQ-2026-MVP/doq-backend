package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload

/**
 * 인입 세션 조회 응답 — 세션·업로드와 **원본 행 전부**(수기·파일).
 *
 * 현황([IngestionState])과 달리 파일에서 나온 행까지 원문째로 준다. 세션 하나가 수만 행일 수 있으므로
 * 이건 요청했을 때만 나가고, 변경 응답·현황 스트림에는 싣지 않는다.
 */
data class IngestionSessionResponse(
    val ingestionId: Long,
    val status: IngestionStatus,
    val uploads: List<IngestionUploadResponse>,
    val records: List<IngestionRecordResponse>,
) {
    constructor(
        ingestion: Ingestion,
        uploads: List<IngestionUpload>,
        records: List<IngestionRecord>,
    ) : this(
        ingestionId = requireNotNull(ingestion.id),
        status = ingestion.status,
        uploads = uploads.map(::IngestionUploadResponse),
        records = records.map(::IngestionRecordResponse),
    )
}
