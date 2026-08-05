package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus

/**
 * 인입 세션 변경 응답 — 어떤 세션이 생겼/바뀌었고 현재 상태가 무엇인지.
 * 파일 업로드·수기 입력 모두 세션을 돌려주므로 두 필드로 공통 표현한다.
 */
data class IngestionMutationResponse(
    val ingestionId: Long,
    val status: IngestionStatus,
) {
    constructor(ingestion: Ingestion) : this(
        ingestionId = requireNotNull(ingestion.id),
        status = ingestion.status
    )
}
