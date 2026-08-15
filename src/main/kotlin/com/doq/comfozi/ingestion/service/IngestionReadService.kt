package com.doq.comfozi.ingestion.service

import com.doq.comfozi.ingestion.domain.Ingestion
import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.ingestion.domain.IngestionUpload

/** 인입 조회 서비스 — 세션·업로드·원본 행 읽기. */
interface IngestionReadService {

    /**
     * 전체 세션 요약 (등록순) — 목록 화면이 "어떤 세션이 있었나"를 서버에서 받는 경로.
     *
     * 세션 목록이 서버에 없으면 화면이 스스로 기억할 수밖에 없고, 그러면 새로고침·다른 브라우저에서
     * 세션이 사라진다. 행 내용은 담지 않는다 — 목록은 세션당 한 줄만 필요하다.
     */
    fun getSessions(): List<IngestionSessionSummary>

    /** 세션 조회 — 없으면 예외. */
    fun getSession(ingestionId: Long): Ingestion

    /**
     * 세션 현황 — 올라온 파일들과 **수기 행들**. 파일에서 나온 행은 담지 않는다.
     * 변화마다 반복해 읽는 현황 스트림용이라, 3만 행짜리 파일이 올라와도 비용이 늘지 않는다.
     */
    fun getStatus(ingestionId: Long): IngestionSessionStatus

    /** 세션의 업로드들 (id 오름차순). 수기 입력은 업로드가 아니므로 포함되지 않는다. */
    fun getUploads(ingestionId: Long): List<IngestionUpload>

    /** 세션에 속한 업로드 1건 — 없거나 다른 세션의 업로드면 없는 것으로 취급(404). */
    fun getUpload(ingestionId: Long, uploadId: Long): IngestionUpload

    /** 세션의 원본 행들 (id 오름차순). */
    fun getRecords(ingestionId: Long): List<IngestionRecord>
}

/**
 * 세션 현황 스냅샷 — 화면이 인입 세션에 대해 보여주는 것 전부: 올라온 파일들과 수기 행들.
 * 파일에서 나온 행은 여기 없다(파일 단위로만 보여주므로).
 */
data class IngestionSessionStatus(
    val ingestion: Ingestion,
    val uploads: List<IngestionUpload>,
    val manualRecords: List<IngestionRecord>,
)

/**
 * 목록 한 줄 — 세션 자체와 집계 수치만. 업로드·행 내용은 담지 않는다
 * (목록에서 세션 하나를 열면 그때 [IngestionReadService.getStatus] 로 받는다).
 */
data class IngestionSessionSummary(
    val ingestion: Ingestion,
    val uploadCount: Int,
    val recordCount: Int,
)
