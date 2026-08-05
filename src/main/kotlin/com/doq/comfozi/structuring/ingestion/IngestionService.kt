package com.doq.comfozi.structuring.ingestion

import java.io.InputStream

/** 인입 서비스 — 파일/수기 입력을 세션·행으로 적재. */
interface IngestionService {

    /** 빈 인입 세션([Ingestion], DRAFT) 생성. 수기 입력 등을 이어 붙이기 위한 시작점. */
    fun createSession(): Ingestion

    /**
     * 취합 파일(BATCH_FILE) 업로드 → 세션([Ingestion]) 생성 + 원본 저장 + 원본 행([IngestionRecord]) 적재.
     * 값은 원문 그대로 저장하며 검증/정규화는 하지 않는다(후속 structuring).
     */
    fun createFromBatchFile(fileName: String, contentType: String?, content: InputStream): Ingestion

    /**
     * 기존 DRAFT 세션에 수기 입력 행을 추가한다. 업로드가 없으므로 [IngestionRecord.uploadRef]는 null.
     * 값은 원문 그대로 저장(검증/정규화는 후속). 확정된 세션(PARSED/FAILED)에는 추가 불가.
     */
    fun addManualRecord(ingestionId: Long, input: IngestionManualInput): IngestionRecord
}
