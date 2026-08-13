package com.doq.comfozi.ingestion

import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import kotlin.test.fail

/**
 * 업로드 처리(파싱·추출)는 응답 이후 별도 스레드에서 돈다 — 결과(행·상태)를 보려면 끝날 때까지 기다린다.
 *
 * 세션의 모든 업로드가 종료 상태(PARSED·PARSE_FAILED)가 되면 반환한다.
 * 업로드가 없는 세션(수기 입력만)에서는 즉시 돌아오므로 어디서 불러도 안전하다.
 */
fun IngestionUploadRepository.awaitParsed(ingestionId: Long, timeoutMillis: Long = 10_000) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    do {
        if (findByIngestionIdOrderByIdAsc(ingestionId).all { it.status.isTerminal }) return
        Thread.sleep(10)
    } while (System.currentTimeMillis() < deadline)

    fail("업로드 파싱이 ${timeoutMillis}ms 안에 끝나지 않음 (ingestion=$ingestionId)")
}
