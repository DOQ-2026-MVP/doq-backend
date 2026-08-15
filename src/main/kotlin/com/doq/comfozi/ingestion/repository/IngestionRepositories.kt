package com.doq.comfozi.ingestion.repository

import com.doq.comfozi.ingestion.domain.Ingestion
import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.ingestion.domain.IngestionUpload
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface IngestionRepository : JpaRepository<Ingestion, Long> {
    /** 전체 세션 (등록순). 목록 화면이 "어떤 세션이 있었나"를 서버에서 받아 가는 경로다. */
    fun findAllByOrderByIdAsc(): List<Ingestion>
}

interface IngestionUploadRepository : JpaRepository<IngestionUpload, Long> {
    fun findByIngestionIdOrderByIdAsc(ingestionId: Long): List<IngestionUpload>

    /** 세션별 업로드 수 — 목록 화면용. 세션마다 세면 N+1 이라 한 번에 모아 센다. */
    @Query("select u.ingestionId, count(u) from IngestionUpload u group by u.ingestionId")
    fun countGroupedByIngestionId(): List<Array<Any>>
}

interface IngestionRecordRepository : JpaRepository<IngestionRecord, Long> {
    fun findByIngestionIdOrderByIdAsc(ingestionId: Long): List<IngestionRecord>

    /**
     * 세션의 수기 행들 (id 오름차순). 파일 출처 행(uploadRef 있음)은 대상 밖이다.
     *
     * 현황 스트림용 — 수기 행은 사람이 손으로 넣는 만큼이라 전부 읽어도 싸지만, 파일 행은 한 파일에
     * 수만 개일 수 있어 여기에 섞지 않는다(파일은 업로드 단위로 보여준다).
     */
    fun findByIngestionIdAndUploadRefUploadIdIsNullOrderByIdAsc(ingestionId: Long): List<IngestionRecord>

    /** 세션별 원본 행 수 — 목록 화면용. 세션마다 세면 N+1 이라 한 번에 모아 센다. */
    @Query("select r.ingestionId, count(r) from IngestionRecord r group by r.ingestionId")
    fun countGroupedByIngestionId(): List<Array<Any>>

    /** 세션의 모든 원본 행 삭제 (수기·파일 무관). */
    fun deleteByIngestionId(ingestionId: Long): Long

    /** 특정 업로드에서 나온 원본 행만 삭제 (수기 행은 uploadRef가 null이라 대상 밖). */
    fun deleteByUploadRefUploadId(uploadId: Long): Long
}
