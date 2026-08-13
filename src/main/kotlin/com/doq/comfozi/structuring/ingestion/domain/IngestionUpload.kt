package com.doq.comfozi.structuring.ingestion.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 업로드 이벤트 (Postgres) — [Ingestion] 세션 안의 한 번의 **파일 업로드** (BATCH_FILE·FILE).
 *
 * 원본 바이트는 파일시스템(support의 FileStorage)에 저장하고 [storageKey]로 참조한다.
 * (수기 입력은 업로드가 아니므로 여기 없음 — [IngestionRecord]만 생성.)
 *
 * NOTE: 필드 잠정. FK는 plain Long (네비게이션 필요 시 @ManyToOne).
 */
@Entity
@Table(name = "ingestion_upload")
class IngestionUpload(
    @Column(nullable = false, updatable = false)
    val ingestionId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val type: IngestionUploadType,

    // 처리 현황 — 추출 단계가 생기면 여기서 진행되므로 불변이 아니다
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: IngestionUploadStatus,

    @Column(nullable = false, updatable = false) val fileName: String,
    @Column(nullable = false, updatable = false) val storageKey: String,
    @Column(updatable = false) val contentType: String? = null,
    @Column(updatable = false) val size: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
