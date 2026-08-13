package com.doq.comfozi.structuring.ingestion.repository

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload
import org.springframework.data.jpa.repository.JpaRepository

interface IngestionRepository : JpaRepository<Ingestion, Long>

interface IngestionUploadRepository : JpaRepository<IngestionUpload, Long> {
    fun findByIngestionIdOrderByIdAsc(ingestionId: Long): List<IngestionUpload>
}

interface IngestionRecordRepository : JpaRepository<IngestionRecord, Long> {
    fun findByIngestionIdOrderByIdAsc(ingestionId: Long): List<IngestionRecord>

    /** 세션의 모든 원본 행 삭제 (수기·파일 무관). */
    fun deleteByIngestionId(ingestionId: Long): Long

    /** 특정 업로드에서 나온 원본 행만 삭제 (수기 행은 uploadRef가 null이라 대상 밖). */
    fun deleteByUploadRefUploadId(uploadId: Long): Long
}
