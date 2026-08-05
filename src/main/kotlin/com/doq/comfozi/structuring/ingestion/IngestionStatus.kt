package com.doq.comfozi.structuring.ingestion

/**
 * 인입 세션([Ingestion]) 라이프사이클 상태.
 *
 * - [DRAFT]  : 세션 열림 — 업로드·행 누적 중(취합 파일 + 원본 파일 + 수기), 확정 전
 * - [PARSED] : 입력 확정·파싱/검증 완료 → structuring 인계
 * - [FAILED] : 파싱/검증 실패
 */
enum class IngestionStatus {
    DRAFT,
    PARSED,
    FAILED,
}
