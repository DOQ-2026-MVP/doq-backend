package com.doq.comfozi.structuring.ingestion.repository

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload
import org.springframework.data.jpa.repository.JpaRepository

interface IngestionRepository : JpaRepository<Ingestion, Long>

interface IngestionUploadRepository : JpaRepository<IngestionUpload, Long>

interface IngestionRecordRepository : JpaRepository<IngestionRecord, Long> {
    fun findByIngestionIdOrderByIdAsc(ingestionId: Long): List<IngestionRecord>
}
