package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionContent
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadRef
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.ingestion.pdf.PdfExtractedItem
import com.doq.comfozi.structuring.ingestion.pdf.PdfRecordExtractor
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.structuring.ingestion.support.FileStorage
import com.doq.comfozi.structuring.ingestion.support.IngestionUploadBatchFileParser
import com.doq.common.config.AppObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import org.springframework.beans.factory.ObjectProvider
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
    // 키 미설정 시 추출기 빈이 없으므로 지연 조회. 없을 때 PDF 업로드 요청만 실패한다(앱 부팅은 정상).
    private val pdfExtractorProvider: ObjectProvider<PdfRecordExtractor>,
) : IngestionService {

    @Transactional
    override fun createSession(): Ingestion = ingestionRepository.save(Ingestion()) // DRAFT

    @Transactional
    override fun createFromBatchFile(input: IngestionBatchFileInput): Ingestion =
        ingestBatchFile(input = input)

    @Transactional
    override fun createFromPdf(input: IngestionPdfInput): Ingestion =
        ingestPdf(input = input)

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
        val session = ingestionRepository.findByIdOrNull(ingestionId)
            ?: throw NoSuchElementException("알 수 없는 Ingestion 세션 $ingestionId")

        check(session.status != IngestionStatus.STRUCTURED) {
            "완료된(STRUCTURED) 세션은 비울 수 없음"
        }

        val uploads = uploadRepository.findByIngestionId(ingestionId)
        uploads.forEach { fileStorage.delete(it.storageKey) } // 저장 원본 정리
        uploadRepository.deleteAll(uploads)
        recordRepository.deleteByIngestionId(ingestionId) // 수기·파일 행 모두
        session.reopen() // 입력 비움 → 재검증 필요, DRAFT로
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
                fileName = input.fileName,
                storageKey = stored.storageKey,
                contentType = input.contentType,
                size = stored.size,
            ),
        )

        recordRepository.saveAll(parsed.toEntities(ingestion.id, upload.id!!))
        return ingestion
    }

    private fun ingestPdf(input: IngestionPdfInput): Ingestion {
        val extractor = pdfExtractorProvider.ifAvailable
            ?: throw IllegalStateException("PDF 추출기가 구성되지 않았습니다 — ANTHROPIC_API_KEY 설정이 필요합니다")

        val bytes = input.content.readBytes() // 추출·저장 두 번 쓰므로 버퍼링
        val items = extractor.extract(input.fileName, bytes) // 추출 먼저 — 실패 시 부작용 없이 오류
        require(items.isNotEmpty()) { "PDF에서 추출된 항목이 없습니다: ${input.fileName}" }

        val ingestion = createSession()
        val stored = fileStorage.store(bytes.inputStream())
        val upload = uploadRepository.save(
            IngestionUpload(
                ingestionId = ingestion.id!!,
                type = IngestionUploadType.FILE,
                fileName = input.fileName,
                storageKey = stored.storageKey,
                contentType = input.contentType,
                size = stored.size,
            ),
        )

        val records = items.mapIndexed { i, item -> item.toRecord(ingestion.id, upload.id!!, rowNo = i + 1) }
        recordRepository.saveAll(records)
        return ingestion
    }

    /** 추출 항목 → 원본 행. content는 캐노니컬 문자열 맵([FileRecordMapper]와 짝), 출처는 FILE + 항목 순번([rowNo]). */
    private fun PdfExtractedItem.toRecord(ingestionId: Long, uploadId: Long, rowNo: Int): IngestionRecord {
        val values = AppObjectMapper.instance.convertValue(this, jacksonTypeRef<Map<String, String?>>())
        return IngestionRecord(
            ingestionId = ingestionId,
            uploadRef = IngestionUploadRef(uploadId = uploadId, uploadType = IngestionUploadType.FILE, rowNo = rowNo),
            content = IngestionContent(values),
        )
    }

    private fun ingestManual(inputs: List<IngestionManualInput>, ingestionId: Long? = null): Ingestion {
        require(inputs.isNotEmpty()) { "수기 입력이 비어 있습니다" }

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
