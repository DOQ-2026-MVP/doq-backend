package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionUploadRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class IngestionReadServiceImpl(
    private val ingestionRepository: IngestionRepository,
    private val uploadRepository: IngestionUploadRepository,
    private val recordRepository: IngestionRecordRepository,
) : IngestionReadService {

    override fun getSession(ingestionId: Long): Ingestion =
        ingestionRepository.findByIdOrNull(ingestionId)
            ?: throw NoSuchElementException("알 수 없는 Ingestion 세션 $ingestionId")

    override fun getStatus(ingestionId: Long): IngestionSessionStatus = IngestionSessionStatus(
        ingestion = getSession(ingestionId),
        uploads = getUploads(ingestionId),
        manualRecords = recordRepository.findByIngestionIdAndUploadRefUploadIdIsNullOrderByIdAsc(ingestionId),
    )

    override fun getUploads(ingestionId: Long): List<IngestionUpload> =
        uploadRepository.findByIngestionIdOrderByIdAsc(ingestionId)

    override fun getUpload(ingestionId: Long, uploadId: Long): IngestionUpload =
        uploadRepository.findByIdOrNull(uploadId)
            ?.takeIf { it.ingestionId == ingestionId }
            ?: throw NoSuchElementException("세션 $ingestionId 에 없는 업로드 $uploadId")

    override fun getRecords(ingestionId: Long): List<IngestionRecord> =
        recordRepository.findByIngestionIdOrderByIdAsc(ingestionId)
}
