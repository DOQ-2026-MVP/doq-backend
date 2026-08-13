package com.doq.comfozi.ingestion.domain

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

    // 처리 현황 — 파싱이 비동기라 업로드 이후에도 바뀐다
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

    /** 파싱 실패 사유 — [IngestionUploadStatus.PARSE_FAILED]일 때만 채워진다. */
    @Column(length = FAILURE_REASON_MAX)
    var failureReason: String? = null
        private set

    /** 파싱 완료 — 원본 행이 적재된 뒤 호출한다. */
    fun markParsed() {
        status = IngestionUploadStatus.PARSED
        failureReason = null
    }

    /** 파싱 실패 — 원본은 남겨 두고 사유만 기록한다(화면에서 확인 후 삭제·재업로드). */
    fun markParseFailed(reason: String) {
        status = IngestionUploadStatus.PARSE_FAILED
        failureReason = reason.take(FAILURE_REASON_MAX)
    }

    private companion object {
        /** DDL(varchar) 길이와 맞춘다 — 원인 체인이 긴 메시지는 잘라 넣는다. */
        const val FAILURE_REASON_MAX = 500
    }
}
