package com.doq.comfozi.ingestion.domain

/**
 * 인입 원본 내용 (스키마리스, jsonb 저장) — 매핑 이전의 원문.
 *
 * - 수기: 입력 9필드(영문 키) 맵
 * - 파일(CSV/XLSX): 헤더 → 셀 값 맵 (원본 열 전부 보존)
 *
 * 원문 → 구조화 필드 매핑은 인입이 아니라 후속 structuring에서 수행한다.
 * 출처(수기/파일)는 [IngestionRecord.uploadRef]로 표현하므로 여기 담지 않는다.
 */
data class IngestionContent(
    val values: Map<String, String?>,
)
