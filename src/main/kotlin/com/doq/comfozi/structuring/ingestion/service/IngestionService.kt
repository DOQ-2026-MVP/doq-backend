package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord

/**
 * 인입 서비스 — 파일/수기 입력을 세션·행으로 적재한다. (조회는 [IngestionReadService])
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

    /**
     * 구조화 실패 → [Ingestion.markFailed]. 이후 재시도 가능.
     * 파이프라인 트랜잭션과 **독립적으로**(REQUIRES_NEW) 커밋돼, 작업이 롤백돼도 FAILED는 남는다.
     */
    fun markFailed(ingestionId: Long): Ingestion

    /**
     * 세션의 입력을 모두 비운다(수정·재시도용) — 원본 행(수기·파일)·업로드·저장 파일 제거 후 DRAFT로 되돌린다.
     * 완료(STRUCTURED) 세션은 비울 수 없음. 반환값은 되돌려진(빈) 세션.
     */
    fun truncate(ingestionId: Long): Ingestion

    /**
     * 업로드 1건을 세션에서 제거한다 — 그 업로드에서 나온 원본 행과 저장 원본까지 함께 지운다.
     * 다른 업로드의 행과 수기 행은 남는다. DRAFT 세션에서만 가능하며, 세션에 속하지 않은
     * 업로드 id면 없는 것으로 취급한다(404).
     */
    fun deleteUpload(ingestionId: Long, uploadId: Long): Ingestion

    /**
     * 원본 행 1건을 삭제한다 (수기·파일 무관 — 구조화 전이라 지워도 깨지는 불변식이 없다).
     * DRAFT·FAILED 세션에서만 가능하며, 세션에 속하지 않은 행 id면 없는 것으로 취급한다(404).
     */
    fun deleteRecord(ingestionId: Long, recordId: Long): Ingestion

    /**
     * 수기 행의 원문을 교체한다(오타 정정 등). 파일 출처 행은 원본 근거라 대상이 아니며(409),
     * 수정은 구조화 이후 검수 단계의 몫이다. DRAFT·FAILED 세션에서만 가능.
     */
    fun updateManualRecord(ingestionId: Long, recordId: Long, input: IngestionManualInput): IngestionRecord
}
