package com.doq.comfozi.structuring.ingestion.domain

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 인입 원본 행 (Postgres) — [Ingestion] 세션 안의 원문 한 행.
 *
 * 출처는 [uploadRef]로 추적한다 (null이면 수기 입력, 있으면 파일 업로드 + 행번호).
 * 원문은 [content](jsonb)에 **그대로** 보관하며, 구조화 필드로의 매핑은 인입이 아니라
 * 후속 structuring에서 수행한다.
 */
@Entity
@Table(name = "ingestion_record")
class IngestionRecord(
    @Column(nullable = false, updatable = false)
    val ingestionId: Long,

    // null = 수기 입력 (업로드 출처 없음)
    @Embedded
    val uploadRef: IngestionUploadRef? = null,

    // 원문 그대로 (매핑 전) — 수기: 9필드 맵, 파일: 헤더→값 맵
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    val content: IngestionContent,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
