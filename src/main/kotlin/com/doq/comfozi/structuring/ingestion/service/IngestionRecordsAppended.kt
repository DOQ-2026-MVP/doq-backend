package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord

/**
 * 세션에 원본 행들이 추가되었음을 알리는 도메인 이벤트.
 * 쓰기 서비스가 발행하고, 커밋 후(AFTER_COMMIT) SSE 등으로 전파된다.
 */
data class IngestionRecordsAppended(
    val ingestionId: Long,
    val records: List<IngestionRecord>,
)
