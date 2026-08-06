package com.doq.comfozi.structuring.detection

/**
 * 예외 탐지 플래그 (요구사항 §6 · 4종).
 *
 * - [MISSING_REQUIRED]     : 필수 필드 공란/공백 → 확인 필요
 * - [SPEC_MISMATCH]        : 규격이 `기존 … / 변경 …` 패턴 → 보류 필요
 * - [UNIT_MISMATCH]        : 단위가 표준집합(PK·BOX·EA·PO) 밖 또는 복수 단위 병기 → 보류 필요
 * - [DUPLICATE_SUSPECTED]  : 중복키 완전 일치, 그룹 내 2번째 이후 → 보류 필요 (크로스 레코드)
 */
enum class ExceptionFlag {
    MISSING_REQUIRED,
    SPEC_MISMATCH,
    UNIT_MISMATCH,
    DUPLICATE_SUSPECTED,
}
