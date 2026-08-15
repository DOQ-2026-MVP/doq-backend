package com.doq.comfozi.inspection.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * 검수 변경 이력 (Postgres) — 한 [InspectionRecord]에 가해진 편집·전이를 시각순으로 남기는 감사 기록.
 *
 * 요구사항 §6(변경 시각·상태). 불변 append-only. 최초값(observed)은 레코드에 있으므로
 * 여기엔 **전체 스냅샷이 아니라 변경분([changes])만** 남긴다. (메모는 레코드의 현재 상태이므로
 * [InspectionRecord.memo]에 둔다 — 이력엔 남기지 않음)
 * - [type]                  : 무슨 변경인지([InspectionChangeType])
 * - [fromStatus]/[toStatus] : 상태 전이의 이전/이후(편집이면 둘 다 null)
 * - [changes]               : 편집으로 바뀐 필드 diff(전이면 빈 목록)
 */
@Entity
@Table(name = "inspection_changelog")
class InspectionChangeLog(
    @Column(name = "inspection_id", nullable = false, updatable = false)
    val inspectionId: Long,

    /** 이력이 붙는 [InspectionRecord]의 id(검수 레코드 PK) — 인입 원본 행 id가 아니다. */
    @Column(name = "inspection_record_id", nullable = false, updatable = false)
    val inspectionRecordId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val type: InspectionChangeType,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    val fromStatus: InspectionRecordStatus?,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", updatable = false)
    val toStatus: InspectionRecordStatus?,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val changes: List<FieldChange>,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    companion object {
        /** 편집(EDIT) 이력 — 상태 전이 없음, 바뀐 필드 [changes]만. */
        fun edited(
            record: InspectionRecord,
            changes: List<FieldChange>
        ) = InspectionChangeLog(
            inspectionId = record.inspectionId,
            inspectionRecordId = requireNotNull(record.id),
            type = InspectionChangeType.EDIT,
            fromStatus = null,
            toStatus = null,
            changes = changes,
        )

        /** 상태 전이(CONFIRM/REJECT) 이력 — [from]→현재 상태. 필드 변경 없음. */
        fun transitioned(
            record: InspectionRecord,
            type: InspectionChangeType,
            from: InspectionRecordStatus,
        ) = InspectionChangeLog(
            inspectionId = record.inspectionId,
            inspectionRecordId = requireNotNull(record.id),
            type = type,
            fromStatus = from,
            toStatus = record.status,
            changes = emptyList(),
        )
    }
}
