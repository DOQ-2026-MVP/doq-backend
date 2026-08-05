package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.structuring.ingestion.support.FileStorage
import com.doq.comfozi.structuring.ingestion.support.IngestionUploadBatchFileParser
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IngestionServiceImpl(
    private val ingestionRepository: IngestionRepository,
    private val uploadRepository: IngestionUploadRepository,
    private val recordRepository: IngestionRecordRepository,
    private val fileStorage: FileStorage,
    private val batchFileParser: IngestionUploadBatchFileParser,
) : IngestionService {

    @Transactional
    override fun createSession(): Ingestion = ingestionRepository.save(Ingestion()) // DRAFT

    @Transactional
    override fun createFromBatchFile(input: IngestionBatchFileInput): Ingestion =
        ingestBatchFile(input = input)

    @Transactional
    override fun continueFromBatchFile(ingestionId: Long, input: IngestionBatchFileInput): Ingestion =
        ingestBatchFile(input = input, ingestionId = ingestionId)

    @Transactional
    override fun createFromManualRecords(inputs: List<IngestionManualInput>): Ingestion =
        ingestManual(inputs = inputs)

    @Transactional
    override fun continueFromManualRecords(ingestionId: Long, inputs: List<IngestionManualInput>): Ingestion =
        ingestManual(inputs = inputs, ingestionId = ingestionId)

    private fun ingestBatchFile(input: IngestionBatchFileInput, ingestionId: Long? = null): Ingestion {
        val bytes = input.content.readBytes() // 저장·파싱 두 번 쓰므로 버퍼링
        val ingestion = resolveDraftSession(ingestionId)

        val stored = fileStorage.store(bytes.inputStream())
        val upload = uploadRepository.save(
            IngestionUpload(
                ingestionId = ingestion.id!!,
                type = IngestionUploadType.BATCH_FILE,
                fileName = input.fileName,
                storageKey = stored.storageKey,
                contentType = input.contentType,
                size = stored.size,
            ),
        )

        val parsed = batchFileParser.parse(input.fileName, bytes)
        recordRepository.saveAll(parsed.toEntities(ingestion.id, upload.id!!))
        return ingestion
    }

    private fun ingestManual(inputs: List<IngestionManualInput>, ingestionId: Long? = null): Ingestion {
        val ingestion = resolveDraftSession(ingestionId)
        recordRepository.saveAll(inputs.map { it.toEntity(ingestion.id!!) })
        return ingestion
    }

    /** ingestionId가 있으면 기존 세션(없으면 예외), 없으면 새 세션. 어느 쪽이든 DRAFT여야 추가 가능. */
    private fun resolveDraftSession(ingestionId: Long?): Ingestion {
        if (ingestionId == null) {
            return createSession()
        }
        val ingestion = ingestionRepository.findByIdOrNull(ingestionId)
            ?: throw NoSuchElementException("알 수 없는 Ingestion 세션 $ingestionId")
        check(ingestion.status == IngestionStatus.DRAFT) {
            "DRAFT 세션에만 추가 가능 (현재 ${ingestion.status})"
        }
        return ingestion
    }
}
