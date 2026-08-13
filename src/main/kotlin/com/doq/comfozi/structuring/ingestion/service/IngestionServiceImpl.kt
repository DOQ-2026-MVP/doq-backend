package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadStatus
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.structuring.ingestion.support.ClassifiedFile
import com.doq.comfozi.structuring.ingestion.support.FileStorage
import com.doq.comfozi.structuring.ingestion.support.IngestionFileClassifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 공개 메소드 순서는 [IngestionService] 를 그대로 따르고, private 헬퍼는 아래에 모아 둔다. */
@Service
class IngestionServiceImpl(
    private val ingestionRepository: IngestionRepository,
    private val uploadRepository: IngestionUploadRepository,
    private val recordRepository: IngestionRecordRepository,
    private val fileStorage: FileStorage,
    private val fileClassifier: IngestionFileClassifier,
    private val eventPublisher: ApplicationEventPublisher,
) : IngestionService {

    @Transactional
    override fun createSession(): Ingestion = ingestionRepository.save(Ingestion()) // DRAFT

    /**
     * 업로드 1건 접수 — 내용으로 처리 경로를 정하고 **원본 보관까지만** 한다.
     *
     * 분류(매직 바이트)는 여기서 끝낸다 — 싸고, 지원하지 않는 형식은 아무것도 저장하지 않은 채
     * 400 으로 돌려보내야 하기 때문이다. 반면 **후속 처리는 비동기**다: 커밋 이후
     * [IngestionUploadStoredListener]가 받아 별도 스레드에서 경로별로 처리한다. 그래서 어느 경로든
     * PARSING 으로 접수되고, 처리 실패는 400 이 아니라 PARSE_FAILED 상태로 남는다.
     */
    @Transactional
    override fun ingestFile(input: IngestionFileInput, ingestionId: Long?): Ingestion {
        val bytes = input.content.readBytes() // 분류·저장에 두 번 쓰므로 버퍼링
        val classified = fileClassifier.classify(bytes) // 지원 형식이 아니면 저장 전에 400

        val type = when (classified) {
            is ClassifiedFile.Document -> IngestionUploadType.FILE
            is ClassifiedFile.BatchFile -> IngestionUploadType.BATCH_FILE
        }

        val ingestion = resolveDraftSession(ingestionId)
        val upload = storeUpload(
            ingestion = ingestion,
            bytes = bytes,
            type = type,
            status = IngestionUploadStatus.PARSING, // 처리는 커밋 이후 — 어느 경로든 접수 상태로 시작
            fileName = input.fileName,
            contentType = input.contentType,
        )

        // 처리 경로가 있든 없든 전부 발행한다 — 커밋 이후에 전달되므로 워커가 이 업로드 행을 볼 수 있다
        eventPublisher.publishEvent(IngestionUploadStored(upload.id!!, classified))
        publishChange(ingestion, IngestionChange.UploadReceived(upload.id!!))
        return ingestion
    }

    @Transactional
    override fun ingestManual(inputs: List<IngestionManualInput>, ingestionId: Long?): Ingestion {
        require(inputs.isNotEmpty()) { "수기 입력이 비어 있습니다" }

        val ingestion = resolveDraftSession(ingestionId)
        recordRepository.saveAll(inputs.map { it.toEntity(ingestion.id!!) })

        publishChange(ingestion, IngestionChange.RecordsAdded(inputs.size))
        return ingestion
    }

    @Transactional
    override fun updateManualRecord(ingestionId: Long, recordId: Long, input: IngestionManualInput): IngestionRecord {
        val session = editableSession(ingestionId)

        val record = ownedRecord(ingestionId, recordId)
        record.replaceContent(input.toContent()) // 파일 출처면 도메인이 거부(409)
        session.reopen() // 입력이 바뀜 → 재검증 필요
        return record
    }

    @Transactional
    override fun deleteRecord(ingestionId: Long, recordId: Long): Ingestion {
        val session = editableSession(ingestionId)

        recordRepository.delete(ownedRecord(ingestionId, recordId))
        session.reopen() // 입력이 바뀜 → 재검증 필요
        return session
    }

    @Transactional
    override fun deleteUpload(ingestionId: Long, uploadId: Long): Ingestion {
        val session = editableSession(ingestionId)
        val upload = ownedUpload(ingestionId, uploadId)

        recordRepository.deleteByUploadRefUploadId(uploadId) // FK(fk_record_upload) 때문에 행 먼저
        uploadRepository.delete(upload)
        fileStorage.delete(upload.storageKey)

        session.reopen() // 입력이 바뀜 → 재검증 필요
        return session
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun markFailed(ingestionId: Long): Ingestion {
        val ingestion = session(ingestionId)

        ingestion.markFailed()
        return ingestion
    }

    /** 세션을 보고 있는 화면에 변화를 알린다 — 전달은 커밋 이후다([IngestionChanged]). */
    private fun publishChange(ingestion: Ingestion, change: IngestionChange) {
        eventPublisher.publishEvent(IngestionChanged(ingestion.id!!, change))
    }

    /** 원본 바이트를 저장하고 업로드 이벤트를 남긴다 — 취합 파일·원본 문서가 공유하는 부분. */
    private fun storeUpload(
        ingestion: Ingestion,
        bytes: ByteArray,
        type: IngestionUploadType,
        status: IngestionUploadStatus,
        fileName: String,
        contentType: String?,
    ): IngestionUpload {
        val stored = fileStorage.store(bytes.inputStream())
        return uploadRepository.save(
            IngestionUpload(
                ingestionId = ingestion.id!!,
                type = type,
                status = status,
                fileName = fileName,
                storageKey = stored.storageKey,
                contentType = contentType,
                size = stored.size,
            ),
        )
    }

    // ── 조회 헬퍼 ────────────────────────────────────────────────────────

    /** 세션 — 없으면 404. */
    private fun session(ingestionId: Long): Ingestion =
        ingestionRepository.findByIdOrNull(ingestionId)
            ?: throw NoSuchElementException("알 수 없는 Ingestion 세션 $ingestionId")

    /** ingestionId가 있으면 기존 세션(없으면 예외), 없으면 새 세션. 어느 쪽이든 DRAFT여야 추가 가능. */
    private fun resolveDraftSession(ingestionId: Long?): Ingestion {
        if (ingestionId == null) {
            return createSession()
        }
        val ingestion = session(ingestionId)
        check(ingestion.status == IngestionStatus.DRAFT) {
            "DRAFT 세션에만 추가 가능 (현재 ${ingestion.status})"
        }
        return ingestion
    }

    /**
     * 입력을 지우거나 고칠 수 있는 세션 — 없으면 404, 완료(STRUCTURED)면 409.
     * 구조화 이후의 수정은 인입이 아니라 검수(inspection) 도메인의 몫이다.
     */
    private fun editableSession(ingestionId: Long): Ingestion {
        val session = session(ingestionId)

        check(session.status != IngestionStatus.STRUCTURED) {
            "완료된(STRUCTURED) 세션의 입력은 변경할 수 없음"
        }
        return session
    }

    /** 해당 세션에 속한 원본 행 — 없거나 다른 세션의 행이면 없는 것으로 취급(404). */
    private fun ownedRecord(ingestionId: Long, recordId: Long): IngestionRecord =
        recordRepository.findByIdOrNull(recordId)
            ?.takeIf { it.ingestionId == ingestionId }
            ?: throw NoSuchElementException("세션 $ingestionId 에 없는 원본 행 $recordId")

    /** 해당 세션에 속한 업로드 — 없거나 다른 세션의 업로드면 없는 것으로 취급(404). */
    private fun ownedUpload(ingestionId: Long, uploadId: Long): IngestionUpload =
        uploadRepository.findByIdOrNull(uploadId)
            ?.takeIf { it.ingestionId == ingestionId }
            ?: throw NoSuchElementException("세션 $ingestionId 에 없는 업로드 $uploadId")
}
