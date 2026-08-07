package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion

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
     * (업로드 단위 삭제는 추후.)
     */
    fun truncate(ingestionId: Long): Ingestion
}
