package com.doq.comfozi.structuring.ingestion

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream

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
    override fun addManualRecord(ingestionId: Long, input: IngestionManualInput): IngestionRecord {
        val ingestion = ingestionRepository.findById(ingestionId).orElseThrow {
            NoSuchElementException("Ingestion not found: $ingestionId")
        }
        require(ingestion.status == IngestionStatus.DRAFT) {
            "DRAFT 세션에만 수기 입력 가능 (현재 ${ingestion.status})"
        }
        return recordRepository.save(input.toEntity(ingestion.id!!))
    }

    @Transactional
    override fun createFromBatchFile(fileName: String, contentType: String?, content: InputStream): Ingestion {
        val bytes = content.readBytes() // 저장·파싱 두 번 쓰므로 버퍼링

        val ingestion = ingestionRepository.save(Ingestion()) // DRAFT

        val stored = fileStorage.store(bytes.inputStream())
        val upload = uploadRepository.save(
            IngestionUpload(
                ingestionId = ingestion.id!!,
                type = IngestionUploadType.BATCH_FILE,
                fileName = fileName,
                storageKey = stored.storageKey,
                contentType = contentType,
                size = stored.size,
            ),
        )

        val parsed = batchFileParser.parse(fileName, bytes)
        recordRepository.saveAll(parsed.toEntities(ingestion.id!!, upload.id!!))

        return ingestion
    }
}
