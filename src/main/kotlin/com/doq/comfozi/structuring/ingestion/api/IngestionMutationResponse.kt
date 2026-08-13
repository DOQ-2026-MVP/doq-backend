package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus

/**
 * 인입 세션 응답 — 세션 식별자·상태와, 조회 시 업로드 현황·원본 행들.
 *
 * 변경(업로드/수기 입력/삭제) 응답은 [uploads]·[records]가 null(세션만 반환), 조회(GET)는 둘 다 채워진다.
 */
data class IngestionMutationResponse(
    val ingestionId: Long,
    val status: IngestionStatus,
    val uploads: List<IngestionUploadResponse>? = null,
    val records: List<IngestionRecordResponse>? = null,
) {
    constructor(
        ingestion: Ingestion,
        uploads: List<IngestionUploadResponse>? = null,
        records: List<IngestionRecordResponse>? = null,
    ) : this(
        ingestionId = requireNotNull(ingestion.id),
        status = ingestion.status,
        uploads = uploads,
        records = records,
    )
}
