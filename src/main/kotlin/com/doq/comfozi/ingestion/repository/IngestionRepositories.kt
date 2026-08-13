package com.doq.comfozi.ingestion.repository

import com.doq.comfozi.ingestion.domain.Ingestion
import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.ingestion.domain.IngestionUpload
import org.springframework.data.jpa.repository.JpaRepository

interface IngestionRepository : JpaRepository<Ingestion, Long>

interface IngestionUploadRepository : JpaRepository<IngestionUpload, Long> {
    fun findByIngestionIdOrderByIdAsc(ingestionId: Long): List<IngestionUpload>
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

    /** 세션의 모든 원본 행 삭제 (수기·파일 무관). */
    fun deleteByIngestionId(ingestionId: Long): Long

    /** 특정 업로드에서 나온 원본 행만 삭제 (수기 행은 uploadRef가 null이라 대상 밖). */
    fun deleteByUploadRefUploadId(uploadId: Long): Long
}
