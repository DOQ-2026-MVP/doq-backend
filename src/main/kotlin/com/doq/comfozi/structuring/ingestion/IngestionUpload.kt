package com.doq.comfozi.structuring.ingestion

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 업로드 이벤트 (Postgres) — [Ingestion] 세션 안의 한 번의 **파일 업로드** (BATCH_FILE·FILE).
 *
 * 원본 바이트는 파일시스템([FileStorage])에 저장하고 [storageKey]로 참조한다.
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

    @Column(nullable = false, updatable = false) val fileName: String,
    @Column(nullable = false, updatable = false) val storageKey: String,
    @Column(updatable = false) val contentType: String? = null,
    @Column(updatable = false) val size: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
