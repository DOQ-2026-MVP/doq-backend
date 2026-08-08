package com.doq.comfozi.inspection.domain

/**
 * 검수 레코드 상태.
 *
 * - [NEW]       : 구조화 결과가 막 인계됨(검수 전)
 * - [CONFIRMED] : 검수 완료·확정
 * - [REJECTED]  : 반려(다시 손봐야 함)
 *
 * 지금은 인계 시 [NEW]만 세팅한다 — 확정/반려 전이는 후속 검수 API에서.
 */
enum class InspectionRecordStatus {
    NEW,
    CONFIRMED,
    REJECTED,
}
