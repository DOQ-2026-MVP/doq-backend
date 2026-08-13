package com.doq.comfozi.ingestion.service

import com.doq.comfozi.ingestion.support.ClassifiedFile
import com.doq.common.config.AsyncConfig
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 업로드 후속 처리의 **실행 시점·경로 분기** 담당 — 업로드가 커밋된 뒤(AFTER_COMMIT)
 * **다른 스레드**에서 처리 경로별 워커를 부른다.
 *
 * 커밋 이후여야 워커가 업로드 행을 볼 수 있고, 다른 스레드여야 업로드 응답이 처리를 기다리지 않는다.
 * 트랜잭션 경계는 워커([IngestionUploadParseService]) 쪽에 있다 — 여기서 트랜잭션을 열면 실패 처리까지
 * 같은 트랜잭션에 묶여 함께 롤백된다.
 */
@Component
class IngestionUploadStoredListener(
    private val parseService: IngestionUploadParseService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async(AsyncConfig.INGESTION_PARSE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUploadStored(event: IngestionUploadStored) {
        val classified = event.classified
        process(event.uploadId) {
            when (classified) {
                is ClassifiedFile.BatchFile -> parseService.parse(event.uploadId, classified.format)

                // TODO: 원본 문서(PDF·이미지) 행 추출 — OCR/LLM 워커가 붙을 자리.
                //  지금은 추출을 지원하지 않아 행 없이 완료 처리한다. 워커가 붙으면 이 자리에서
                //  행을 만들면 되고, 성공·실패 표현(PARSED/PARSE_FAILED)은 그대로 쓴다.
                is ClassifiedFile.Document -> {
                    log.debug("업로드 {} 는 {} 원본 — 행 추출 미지원", event.uploadId, classified.format)
                    parseService.markParsedWithoutRecords(event.uploadId)
                }
            }
        }
    }

    /** 어느 경로든 실패는 같은 방식으로 남긴다 — 예외를 밖으로 던지면 업로드가 PARSING 에 갇힌다. */
    private fun process(uploadId: Long, work: () -> Unit) {
        try {
            work()
        } catch (e: Exception) {
            log.warn("업로드 {} 처리 실패", uploadId, e)
            parseService.markParseFailed(uploadId, reasonOf(e))
        }
    }

    /**
     * 화면에 보일 사유. 파서가 안내용으로 던진 [IllegalArgumentException] 메시지는 그대로 쓰고,
     * 그 밖의 예외(입출력 오류 등)는 내부 사정이라 감춘다 — 원문은 로그에 남는다.
     */
    private fun reasonOf(e: Exception): String =
        (e as? IllegalArgumentException)?.message ?: "파일을 처리하지 못했습니다"
}
