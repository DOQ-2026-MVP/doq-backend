package com.doq.comfozi.ingestion.service

import com.doq.comfozi.ingestion.extraction.DocumentTextExtractor
import com.doq.comfozi.ingestion.extraction.ImageTextExtractor
import com.doq.comfozi.ingestion.extraction.ItemExtractor
import com.doq.comfozi.ingestion.extraction.PdfTextExtractor
import com.doq.comfozi.ingestion.extraction.TableTextItemExtractor
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
    private val pdfTextExtractor: PdfTextExtractor,
    private val imageTextExtractor: ImageTextExtractor,
    // LLM 추출기는 API 키가 있을 때만 존재한다 — 없어도 부팅·업로드는 정상이어야 한다
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
     * 원본 문서에서 항목을 뽑는다 — PDF 는 텍스트 레이어를, 이미지는 OCR 을 거쳐 같은 길로 간다.
     *
     * LLM 추출기가 꺼져 있어도 행은 만든다 — [TableTextItemExtractor] 가 규칙으로 표를 읽고,
     * 그마저 실패하면 원문을 한 행에 담는다. 어느 쪽이든 관찰값이라 검수에서 확인·수정된다.
     *
     * 글자를 꺼낼 방법 자체가 없으면(OCR 미설치) 행 없이 완료 처리한다 — 설치를 안 한 것은
     * 파일 문제가 아니므로 실패(PARSE_FAILED)가 아니다.
     */
    private fun extract(uploadId: Long, classified: ClassifiedFile.Document) {
        val textExtractor = textExtractorFor(classified.format)
        if (textExtractor == null) {
            log.debug("업로드 {} 는 {} 원본 — 글자를 꺼낼 방법이 없어 행을 만들지 않는다", uploadId, classified.format)
            parseService.markParsedWithoutRecords(uploadId)
            return
        }

        val extractor = itemExtractor.getIfAvailable() ?: TableTextItemExtractor
        parseService.extract(uploadId, textExtractor, extractor, sourceType = classified.format)
    }

    /** 형식별로 글자를 꺼내는 방법. OCR 이 설치돼 있지 않으면 이미지는 방법이 없다(null). */
    private fun textExtractorFor(format: String): DocumentTextExtractor? = when (format) {
        PDF -> pdfTextExtractor
        else -> imageTextExtractor.takeIf { it.isAvailable }
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
