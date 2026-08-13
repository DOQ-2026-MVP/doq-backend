package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadStatus
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.structuring.ingestion.support.FileStorage
import com.doq.comfozi.structuring.ingestion.support.IngestionUploadBatchFileParser
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun markFailed(ingestionId: Long): Ingestion {
        val ingestion = ingestionRepository.findByIdOrNull(ingestionId)
            ?: throw NoSuchElementException("알 수 없는 Ingestion 세션 $ingestionId")

        ingestion.markFailed()
        return ingestion
    }

    @Transactional
    override fun truncate(ingestionId: Long): Ingestion {
        val session = editableSession(ingestionId)

        val uploads = uploadRepository.findByIngestionIdOrderByIdAsc(ingestionId)
        uploads.forEach { fileStorage.delete(it.storageKey) } // 저장 원본 정리
        uploadRepository.deleteAll(uploads)
        recordRepository.deleteByIngestionId(ingestionId) // 수기·파일 행 모두
        session.reopen() // 입력 비움 → 재검증 필요, DRAFT로
        return session
    }

    @Transactional
    override fun deleteUpload(ingestionId: Long, uploadId: Long): Ingestion {
        val session = editableSession(ingestionId)

        val upload = uploadRepository.findByIdOrNull(uploadId)
            ?.takeIf { it.ingestionId == ingestionId } // 다른 세션의 업로드는 없는 것으로 취급
            ?: throw NoSuchElementException("세션 $ingestionId 에 없는 업로드 $uploadId")

        recordRepository.deleteByUploadRefUploadId(uploadId) // FK(fk_record_upload) 때문에 행 먼저
        uploadRepository.delete(upload)
        fileStorage.delete(upload.storageKey)

        session.reopen() // 입력이 바뀜 → 재검증 필요
        return session
    }

    private fun ingestBatchFile(input: IngestionBatchFileInput, ingestionId: Long? = null): Ingestion {
        val bytes = input.content.readBytes() // 저장·파싱 두 번 쓰므로 버퍼링
        val parsed = batchFileParser.parse(input.fileName, bytes) // 형식·필수헤더 검증 먼저 — 실패 시 부작용 없이 400
        val ingestion = resolveDraftSession(ingestionId)

        val stored = fileStorage.store(bytes.inputStream())
        val upload = uploadRepository.save(
            IngestionUpload(
                ingestionId = ingestion.id!!,
                type = IngestionUploadType.BATCH_FILE,
                status = IngestionUploadStatus.PARSED, // 파싱을 통과해야 여기 도달
                fileName = input.fileName,
                storageKey = stored.storageKey,
                contentType = input.contentType,
                size = stored.size,
            ),
        )

        recordRepository.saveAll(parsed.toEntities(ingestion.id, upload.id!!))
        return ingestion
    }

    private fun ingestManual(inputs: List<IngestionManualInput>, ingestionId: Long? = null): Ingestion {
        require(inputs.isNotEmpty()) { "수기 입력이 비어 있습니다" }

        val ingestion = resolveDraftSession(ingestionId)
        recordRepository.saveAll(inputs.map { it.toEntity(ingestion.id!!) })
        return ingestion
    }

    /**
     * 입력을 지우거나 고칠 수 있는 세션 — 없으면 404, 완료(STRUCTURED)면 409.
     * 구조화 이후의 수정은 인입이 아니라 검수(inspection) 도메인의 몫이다.
     */
    private fun editableSession(ingestionId: Long): Ingestion {
        val session = ingestionRepository.findByIdOrNull(ingestionId)
            ?: throw NoSuchElementException("알 수 없는 Ingestion 세션 $ingestionId")

        check(session.status != IngestionStatus.STRUCTURED) {
            "완료된(STRUCTURED) 세션의 입력은 변경할 수 없음"
        }
        return session
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
