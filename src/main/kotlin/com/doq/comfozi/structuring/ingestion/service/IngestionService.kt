package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion

/**
 * 인입 서비스 — 파일/수기 입력을 세션·행으로 적재.
 * `create*`는 **새 세션**을 만들고, `continue*`는 **기존 DRAFT 세션**에 이어붙인다.
 * 값은 원문 그대로 저장하며 검증/정규화는 하지 않는다(후속 structuring).
 */
interface IngestionService {

    /** 빈 인입 세션(DRAFT) 생성. */
    fun createSession(): Ingestion

    /** 취합 파일(BATCH_FILE) 업로드로 새 세션 생성 + 원본 저장 + 원본 행 적재. */
    fun createFromBatchFile(input: IngestionBatchFileInput): Ingestion

    /** 수기 입력들로 새 세션 생성 + 행 적재(uploadRef=null). */
    fun createFromManualRecords(inputs: List<IngestionManualInput>): Ingestion

    /** 취합 파일 업로드를 기존 DRAFT 세션에 이어붙인다. */
    fun continueFromBatchFile(ingestionId: Long, input: IngestionBatchFileInput): Ingestion

    /** 수기 입력들을 기존 DRAFT 세션에 이어붙인다. */
    fun continueFromManualRecords(ingestionId: Long, inputs: List<IngestionManualInput>): Ingestion
}
