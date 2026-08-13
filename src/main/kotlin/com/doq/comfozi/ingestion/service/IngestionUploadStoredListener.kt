package com.doq.comfozi.ingestion.service

import com.doq.comfozi.ingestion.extraction.ItemExtractor
import com.doq.comfozi.ingestion.support.ClassifiedFile
import com.doq.common.config.AsyncConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
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
    // 추출기는 API 키가 있을 때만 존재한다 — 없어도 부팅·업로드는 정상이어야 한다
    private val itemExtractor: ObjectProvider<ItemExtractor>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async(AsyncConfig.INGESTION_PARSE_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUploadStored(event: IngestionUploadStored) {
        val classified = event.classified
        process(event.uploadId) {
            when (classified) {
                is ClassifiedFile.BatchFile -> parseService.parse(event.uploadId, classified.format)

                is ClassifiedFile.Document -> extract(event.uploadId, classified)
            }
        }
    }

    /**
     * 원본 문서에서 항목을 뽑는다 — 지금은 **PDF 만** 지원한다.
     *
     * 이미지(PNG·JPEG)는 회전·원근 왜곡이 있는 촬영본이라 전처리가 필요해 아직 경로가 없고,
     * 추출기는 API 키가 있을 때만 켜진다. 둘 중 하나라도 아니면 행 없이 완료 처리한다 —
     * "기계가 할 일이 없다"는 뜻이지 실패가 아니므로 PARSE_FAILED 가 아니다.
     *
     * TODO: 이미지 추출(OCR/vision)이 붙을 자리. 여기서 행을 만들면 상태·실패 사유·현황 스트림은
     *  그대로 쓴다.
     */
    private fun extract(uploadId: Long, classified: ClassifiedFile.Document) {
        val extractor = itemExtractor.getIfAvailable()
        if (classified.format != PDF || extractor == null) {
            log.debug("업로드 {} 는 {} 원본 — 행 추출 미지원(추출기={})", uploadId, classified.format, extractor != null)
            parseService.markParsedWithoutRecords(uploadId)
            return
        }
        parseService.extract(uploadId, extractor, sourceType = classified.format)
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

    private companion object {
        /** 지금 추출을 지원하는 유일한 문서 형식 ([com.doq.comfozi.ingestion.support.ClassifiedFile] 의 값). */
        const val PDF = "PDF"
    }
}
