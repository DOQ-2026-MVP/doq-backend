package com.doq.comfozi.ingestion.domain

/**
 * 인입 세션([Ingestion]) 라이프사이클 상태.
 *
 * ```
 * DRAFT ──struct 성공──▶ STRUCTURED (종료)
 *   │                      ▲
 *   └──struct 실패──▶ FAILED ─재시도(struct)─┘
 *                       └─재시도 실패─▶ FAILED
 * ```
 *
 * - [DRAFT]      : 세션 열림 — 업로드·행 누적 중(취합 파일 + 원본 파일 + 수기). 구조화 대상.
 * - [STRUCTURED] : 구조화 완료 — inspection으로 인계됨 (종료 상태)
 * - [FAILED]     : 구조화 실패 — **재시도 가능** (다시 struct)
 *
 * (형식·필수헤더 검증은 업로드 시점에 fail-fast(400)로 끝나고, 값 누락·단위 오류 등은
 * structuring detection이 플래그로 surface하므로 별도 검증 상태를 두지 않는다.)
 */
enum class IngestionStatus {
    DRAFT,
    STRUCTURED,
    FAILED,
}
