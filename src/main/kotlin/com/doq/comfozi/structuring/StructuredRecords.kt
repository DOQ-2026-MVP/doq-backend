package com.doq.comfozi.structuring

/**
 * 구조화 결과 이벤트 — 한 인입 세션의 구조화 완료본(레코드별 [StructuredRecord] 묶음).
 *
 * structuring → inspection 인계 계약. inspection이 받아 **Inbox 1개 + InboxItem N개**로 영속한다.
 * (structuring이 세션 단위 계산 완료본을 한 번에 실어 넘기고, inspection은 저장만 한다.)
 */
data class StructuredRecords(
    val ingestionId: Long,
    val records: List<StructuredRecord>,
)
