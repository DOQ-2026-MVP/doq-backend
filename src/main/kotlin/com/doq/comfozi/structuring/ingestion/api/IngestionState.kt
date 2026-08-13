package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.service.IngestionSessionStatus
import java.time.LocalDateTime

/**
 * 인입 세션 현황 — 입력 화면이 세션에 대해 보여주는 것 전부: 올라온 파일들과 수기 행들.
 *
 * **변경 응답과 현황 스트림이 같은 이 타입을 쓴다.** 적재·삭제 결과로 받든 스트림으로 받든 화면이
 * 다룰 모델이 하나여야 하기 때문이다(스트림은 여기에 계기만 얹는다 — [IngestionStateEvent]).
 *
 * 파일에서 나온 행은 담지 않는다 — 화면이 파일은 업로드 단위로 보여주기 때문이고, 덕분에 3만 행짜리
 * 파일이 올라와도 크기가 늘지 않는다. 행 원문이 필요한 곳은 세션 조회다([IngestionSessionResponse]).
 */
data class IngestionState(
    val ingestionId: Long,
    val status: IngestionStatus,
    val uploads: List<IngestionUploadResponse>,
    val manualRecords: List<IngestionManualRecordSummary>,
) {
    constructor(status: IngestionSessionStatus) : this(
        ingestionId = requireNotNull(status.ingestion.id),
        status = status.ingestion.status,
        uploads = status.uploads.map(::IngestionUploadResponse),
        manualRecords = status.manualRecords.map(::IngestionManualRecordSummary),
    )
}

/** 수기 행 요약 — 목록에 줄 하나를 그리는 데 필요한 만큼. 원문은 세션 조회에서 읽는다. */
data class IngestionManualRecordSummary(
    val id: Long,
    val createdAt: LocalDateTime,
) {
    constructor(record: IngestionRecord) : this(
        id = requireNotNull(record.id),
        createdAt = record.createdAt,
    )
}
