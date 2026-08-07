package com.doq.comfozi.inspection.inbox.domain

import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.mapping.MappedRecord
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 검수 인박스 항목 (Postgres) — structuring이 넘긴 구조화 결과 1건을 사람이 검수·수정한다.
 *
 * [Inbox]([inboxId])에 소속된 항목. 원본 근거는 [recordId]·[uploadType]·[rowNo]로 추적.
 * - [observed] : 시스템 관찰값(매핑·정규화 결과) — **불변 스냅샷**
 * - [current]  : 검수자가 고치는 **편집본** (초기엔 observed 복사)
 * - [flags]    : detection 이상 플래그(그대로 실어 표시)
 */
@Entity
@Table(name = "inbox_item")
class InboxItem(
    @Column(name = "inbox_id", nullable = false, updatable = false)
    val inboxId: Long,

    @Column(nullable = false, updatable = false)
    val recordId: Long,

    // 원본 근거(SourceRef) — 파일 출처면 채워지고 수기면 null
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_type", updatable = false)
    val uploadType: IngestionUploadType?,

    @Column(name = "row_no", updatable = false)
    val rowNo: Int?,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val observed: MappedRecord,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_values", nullable = false)
    var current: MappedRecord,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val flags: Set<AnomalyRuleBasedFlag>,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: InboxItemStatus = InboxItemStatus.NEW,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
