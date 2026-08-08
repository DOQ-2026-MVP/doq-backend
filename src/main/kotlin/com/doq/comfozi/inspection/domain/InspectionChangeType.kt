package com.doq.comfozi.inspection.domain

/**
 * 검수 변경 이력의 변경 유형.
 *
 * - [EDIT]    : 편집본(current) 교체
 * - [CONFIRM] : 확정 전이
 * - [REJECT]  : 반려 전이
 */
enum class InspectionChangeType {
    EDIT,
    CONFIRM,
    REJECT,
}
