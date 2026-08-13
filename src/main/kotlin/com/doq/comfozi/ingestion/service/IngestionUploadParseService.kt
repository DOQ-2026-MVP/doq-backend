package com.doq.comfozi.ingestion.service

import com.doq.comfozi.ingestion.domain.IngestionUpload
import com.doq.comfozi.ingestion.domain.IngestionUploadStatus
import com.doq.comfozi.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.ingestion.support.BatchFileFormat
import com.doq.comfozi.ingestion.support.FileStorage
import com.doq.comfozi.ingestion.support.IngestionUploadBatchFileParser
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 업로드 처리 워커 — 저장된 원본을 읽어 원본 행으로 풀고 업로드를 종료 상태로 옮긴다.
 *
 * 호출은 [IngestionUploadStoredListener]가 업로드 커밋 이후 별도 스레드에서 한다.
 * 성공·실패를 **각각 다른 트랜잭션**으로 나눈 이유: 성공 경로의 트랜잭션이 깨진 채로
 * 같은 트랜잭션에서 실패 상태를 쓰면 그 쓰기까지 함께 롤백되어 업로드가 PARSING에 갇힌다.
 */
@Service
class IngestionUploadParseService(
    private val uploadRepository: IngestionUploadRepository,
    private val recordRepository: IngestionRecordRepository,
    private val fileStorage: FileStorage,
    private val batchFileParser: IngestionUploadBatchFileParser,
    private val eventPublisher: ApplicationEventPublisher,
) {

    /**
     * 파싱 → 원본 행 적재 → PARSED. 실패하면 예외를 그대로 던져 호출자가 [markParseFailed]로 넘긴다.
     *
     * 대기 중 업로드가 지워졌거나(세션 비우기·업로드 삭제) 이미 처리됐으면 조용히 지나간다 —
     * 중복 이벤트나 삭제와의 경쟁에서 유령 행을 만들지 않기 위해서다.
     */
    @Transactional
    fun parse(uploadId: Long, format: BatchFileFormat) {
        val upload = uploadRepository.findByIdOrNull(uploadId) ?: return
        if (upload.status != IngestionUploadStatus.PARSING) return

        val bytes = fileStorage.load(upload.storageKey).use { it.readBytes() }
        val content = batchFileParser.parse(bytes, format)

        recordRepository.saveAll(content.toEntities(upload.ingestionId, uploadId))
        upload.markParsed()
        publishSettled(upload)
    }

    /**
     * 행을 만들지 않고 처리 완료로 끝낸다 — 추출을 지원하지 않는 원본 문서(PDF·이미지)용.
     * 기계가 그 파일에 대해 할 일이 없다는 뜻이라 PARSING 에 남겨 두지 않는다(구조화가 막힌다).
     */
    @Transactional
    fun markParsedWithoutRecords(uploadId: Long) {
        val upload = uploadRepository.findByIdOrNull(uploadId) ?: return
        if (upload.status != IngestionUploadStatus.PARSING) return

        upload.markParsed()
        publishSettled(upload)
    }

    /**
     * 실패 사유를 남긴다. [parse]의 트랜잭션이 롤백된 뒤 **새 트랜잭션**에서 돌아야 하므로
     * REQUIRES_NEW다(구조화 실패를 남기는 [IngestionService.markFailed]와 같은 이유).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markParseFailed(uploadId: Long, reason: String) {
        val upload = uploadRepository.findByIdOrNull(uploadId) ?: return
        if (upload.status != IngestionUploadStatus.PARSING) return

        upload.markParseFailed(reason)
        publishSettled(upload)
    }

    /**
     * 처리가 끝났음을 세션 현황 스트림에 알린다 — 성공·실패 어느 쪽이든 같은 계기다
     * (결과는 구독자가 현황에서 읽는다). 전달은 이 트랜잭션이 커밋된 뒤다.
     */
    private fun publishSettled(upload: IngestionUpload) {
        eventPublisher.publishEvent(
            IngestionChanged(upload.ingestionId, IngestionChange.UploadSettled(requireNotNull(upload.id))),
        )
    }
}
