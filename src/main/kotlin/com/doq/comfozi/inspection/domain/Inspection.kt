package com.doq.comfozi.inspection.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 검수(Inspection) 애그리거트 (Postgres) — 한 인입 세션의 구조화 결과를 담는 **검수 단위**(struct 1회 = Inspection 1개).
 *
 * 레코드([InspectionRecord])는 이 검수([id])에 소속된다. 원본 인입 세션은 [ingestionId]로 추적.
 * (진행률 롤업 등 검수 단위 상태는 추후 — 지금은 그룹핑 루트.)
 */
@Entity
@Table(name = "inspection")
class Inspection(
    @Column(nullable = false, updatable = false)
    val ingestionId: Long,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
