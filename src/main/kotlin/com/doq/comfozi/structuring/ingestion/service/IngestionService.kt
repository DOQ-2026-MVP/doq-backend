package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord

/**
 * 인입 서비스 — 파일/수기 입력을 세션·행으로 적재한다. (조회는 [IngestionReadService])
 * 적재는 upsert 다 — `ingestionId` 가 null 이면 새 세션, 있으면 그 세션에 이어붙인다.
 * 값은 원문 그대로 저장하며 검증/정규화는 하지 않는다(후속 structuring).
 *
 * 메소드 순서: **세션 → 적재(입력 종류별) → 수정 → 삭제 → 파이프라인 콜백**.
 * 삭제는 대상 범위가 좁은 것부터(행 → 업로드 → 세션 전체). 구현체·컨트롤러도 이 순서를 따른다.
 */
interface IngestionService {

    /** 빈 인입 세션(DRAFT) 생성. */
    fun createSession(): Ingestion

    /**
     * 파일 업로드 적재(upsert) — [ingestionId] 가 null 이면 **새 DRAFT 세션**을 만들고,
     * 있으면 그 세션에 이어붙인다(DRAFT 가 아니면 409).
     *
     * 처리 경로는 **내용으로 판정**한다: 취합 표 파일(CSV·XLSX)이면 파싱해 원본 행을 적재하고,
     * 원본 증빙 문서(PDF·이미지)면 보관만 한다(행 자동 추출은 미지원 — 수기 입력으로 보완).
     *
     * 이 메소드는 **원본 보관까지만** 하고 돌아온다 — 파싱·추출은 커밋 이후 비동기로 진행되며
     * 그동안 업로드는 `PARSING` 이다. 따라서 반환 시점에 아직 행이 없고, **처리 실패는 예외가 아니라
     * `PARSE_FAILED` 상태**로 드러난다(세션 조회의 `uploads[]`). 지원하지 않는 파일 형식만 저장 전에
     * 예외로 거른다.
     */
    fun ingestFile(input: IngestionFileInput, ingestionId: Long? = null): Ingestion

    /**
     * 수기 입력 적재(upsert) — [ingestionId] 가 null 이면 새 DRAFT 세션, 있으면 그 세션에 이어붙인다.
     * 행은 업로드 출처 없이 생성된다(uploadRef=null).
     */
    fun ingestManual(inputs: List<IngestionManualInput>, ingestionId: Long? = null): Ingestion

    /**
     * 수기 행의 원문을 교체한다(오타 정정 등). 파일 출처 행은 원본 근거라 대상이 아니며(409),
     * 수정은 구조화 이후 검수 단계의 몫이다. DRAFT·FAILED 세션에서만 가능.
     */
    fun updateManualRecord(ingestionId: Long, recordId: Long, input: IngestionManualInput): IngestionRecord

    /**
     * 원본 행 1건을 삭제한다 (수기·파일 무관 — 구조화 전이라 지워도 깨지는 불변식이 없다).
     * DRAFT·FAILED 세션에서만 가능하며, 세션에 속하지 않은 행 id면 없는 것으로 취급한다(404).
     */
    fun deleteRecord(ingestionId: Long, recordId: Long): Ingestion

    /**
     * 업로드 1건을 세션에서 제거한다 — 그 업로드에서 나온 원본 행과 저장 원본까지 함께 지운다.
     * 다른 업로드의 행과 수기 행은 남는다. DRAFT·FAILED 세션에서만 가능하며, 세션에 속하지 않은
     * 업로드 id면 없는 것으로 취급한다(404).
     */
    fun deleteUpload(ingestionId: Long, uploadId: Long): Ingestion

    /**
     * 세션의 입력을 모두 비운다(수정·재시도용) — 원본 행(수기·파일)·업로드·저장 파일 제거 후 DRAFT로 되돌린다.
     * 완료(STRUCTURED) 세션은 비울 수 없음. 반환값은 되돌려진(빈) 세션.
     */
    fun truncate(ingestionId: Long): Ingestion

    /**
     * 구조화 실패 → [Ingestion.markFailed]. 이후 재시도 가능.
     * 파이프라인 트랜잭션과 **독립적으로**(REQUIRES_NEW) 커밋돼, 작업이 롤백돼도 FAILED는 남는다.
     */
    fun markFailed(ingestionId: Long): Ingestion
}
