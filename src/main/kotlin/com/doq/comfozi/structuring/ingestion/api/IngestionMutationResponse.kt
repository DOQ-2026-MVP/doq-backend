package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus

/**
 * 인입 세션 응답 — 세션 식별자·상태와, 조회 시 원본 행들.
 *
 * 변경(업로드/수기 입력) 응답은 [records]가 null(세션만 반환), 조회(GET)는 [records]가 채워진다.
 */
data class IngestionMutationResponse(
    val ingestionId: Long,
    val status: IngestionStatus,
    val records: List<IngestionRecordResponse>? = null,
) {
    constructor(ingestion: Ingestion, records: List<IngestionRecordResponse>? = null) : this(
        ingestionId = requireNotNull(ingestion.id),
        status = ingestion.status,
        records = records,
    )
}
