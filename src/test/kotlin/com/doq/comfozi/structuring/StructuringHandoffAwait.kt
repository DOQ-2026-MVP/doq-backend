package com.doq.comfozi.structuring

import com.doq.comfozi.ingestion.domain.IngestionStatus
import com.doq.comfozi.ingestion.repository.IngestionRepository
import com.doq.comfozi.inspection.domain.Inspection
import com.doq.comfozi.inspection.repository.InspectionRepository
import org.springframework.data.repository.findByIdOrNull
import kotlin.test.fail

/**
 * 구조화 결과 인계(검수 인박스 적재 + 세션 STRUCTURED 전이)는 커밋 이후 별도 스레드에서 돈다 —
 * 결과를 보려면 인박스가 생길 때까지 기다린다.
 *
 * 적재와 상태 전이가 한 트랜잭션이므로 인박스가 보이면 세션도 이미 STRUCTURED 다.
 */
fun InspectionRepository.awaitInspection(ingestionId: Long, timeoutMillis: Long = 10_000): Inspection {
    val deadline = System.currentTimeMillis() + timeoutMillis
    do {
        findByIngestionId(ingestionId)?.let { return it }
        Thread.sleep(10)
    } while (System.currentTimeMillis() < deadline)

    fail("세션 $ingestionId 의 검수 인박스가 ${timeoutMillis}ms 안에 만들어지지 않음")
}

/** 인계가 끝나 세션이 STRUCTURED 가 될 때까지 기다린다 (인박스 적재와 같은 트랜잭션이다). */
fun IngestionRepository.awaitStructured(ingestionId: Long, timeoutMillis: Long = 10_000) =
    awaitStatus(ingestionId, IngestionStatus.STRUCTURED, timeoutMillis)

/** 세션이 [expected] 상태가 될 때까지 기다린다 — 인계가 비동기라 상태 전이도 나중에 온다. */
fun IngestionRepository.awaitStatus(
    ingestionId: Long,
    expected: IngestionStatus,
    timeoutMillis: Long = 10_000,
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    do {
        if (findByIdOrNull(ingestionId)?.status == expected) return
        Thread.sleep(10)
    } while (System.currentTimeMillis() < deadline)

    fail("세션 $ingestionId 이 ${timeoutMillis}ms 안에 $expected 가 되지 않음")
}
