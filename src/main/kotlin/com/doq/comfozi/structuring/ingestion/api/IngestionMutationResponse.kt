package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus

/**
 * 인입 세션 변경 응답 — 업로드/수기 입력 후 어떤 세션이 생겼/바뀌었는지.
 *
 * [status]는 세션을 돌려주는 경우(파일 업로드)만, [recordId]는 행 1건을 돌려주는 경우(수기 입력)만 채워진다.
 * (서비스가 엔티티를 반환하므로 병합 후 그에 맞춰 둘 중 있는 쪽만 매핑.)
 */
data class IngestionMutationResponse(
    val ingestionId: Long,
    val status: IngestionStatus? = null,
    val recordId: Long? = null,
)
