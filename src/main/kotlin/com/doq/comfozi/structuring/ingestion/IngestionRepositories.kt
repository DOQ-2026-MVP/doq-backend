package com.doq.comfozi.structuring.ingestion

import org.springframework.data.jpa.repository.JpaRepository

interface IngestionRepository : JpaRepository<Ingestion, Long>

interface IngestionUploadRepository : JpaRepository<IngestionUpload, Long>

interface IngestionRecordRepository : JpaRepository<IngestionRecord, Long> {
    fun findByIngestionIdOrderByIdAsc(ingestionId: Long): List<IngestionRecord>
}
