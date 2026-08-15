package com.doq.comfozi.inspection.domain

/**
 * 검수 변경 이력의 변경 유형.
 *
 * - [EDIT]    : 편집본(current)·메모 교체
 * - [CONFIRM] : 확정 전이
 * - [REJECT]  : 반려 전이
 * - [RESET]   : 초기화 — 편집본을 관찰값으로 되돌리고 NEW 로 전이
 */
enum class InspectionChangeType {
    EDIT,
    CONFIRM,
    REJECT,
    RESET,
}
