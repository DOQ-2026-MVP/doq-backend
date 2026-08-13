package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.service.IngestionSessionStatus

/**
 * 인입 세션 현황 — 입력 화면이 세션에 대해 보여주는 것 전부: 올라온 파일들과 수기 행들.
 *
 * **조회·변경 응답·현황 스트림이 모두 같은 이 타입을 쓴다.** 조회로 받든 적재·삭제 결과로 받든
 * 스트림으로 받든 화면이 다룰 모델이 하나여야 하기 때문이다(스트림은 여기에 계기만 얹는다 —
 * [IngestionStateEvent]).
 *
 * [manuals]는 수기로 넣은 행들이다. 파일에서 나온 행은 담지 않는다 — 화면이 파일은 업로드 단위로
 * 보여주기 때문이고, 덕분에 3만 행짜리 파일이 올라와도 크기가 늘지 않는다.
 */
data class IngestionState(
    val ingestionId: Long,
    val status: IngestionStatus,
    val uploads: List<IngestionUploadResponse>,
    val manuals: List<IngestionRecordResponse>,
) {
    constructor(status: IngestionSessionStatus) : this(
        ingestionId = requireNotNull(status.ingestion.id),
        status = status.ingestion.status,
        uploads = status.uploads.map(::IngestionUploadResponse),
        manuals = status.manualRecords.map(::IngestionRecordResponse),
    )
}
