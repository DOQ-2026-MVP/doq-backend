package com.doq.comfozi.inspection.domain

import com.doq.comfozi.structuring.detection.AnomalyRule
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
 * 검수 레코드 (Postgres) — structuring이 넘긴 구조화 결과 1건을 사람이 검수·수정한다.
 *
 * [Inspection]([inspectionId])에 소속된 레코드. 원본 근거는 [recordId]·[uploadType]·[rowNo]로 추적.
 * - [observed] : 시스템 관찰값(매핑·정규화 결과) — **불변 스냅샷**
 * - [current]  : 검수자가 고치는 **편집본** (초기엔 observed 복사)
 * - [flags]    : detection 이상 플래그(그대로 실어 표시)
 */
@Entity
@Table(name = "inspection_record")
class InspectionRecord(
    @Column(name = "inspection_id", nullable = false, updatable = false)
    val inspectionId: Long,

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
    @Column(nullable = false)
    var flags: Set<AnomalyRuleBasedFlag>,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: InspectionRecordStatus = InspectionRecordStatus.NEW,

    /** 현재 검수 메모 — 최신 확정/반려 사유. 전이 시 갱신(없이 호출하면 비워짐). */
    @Column(length = 1000)
    var memo: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 편집본([current]) 교체 — 검수자가 관찰값을 교정한다. 확정된 레코드는 잠겨 있어 편집 불가(먼저 반려해야 함). */
    fun edit(values: MappedRecord) {
        check(status != InspectionRecordStatus.CONFIRMED) {
            "확정된(CONFIRMED) 레코드는 편집할 수 없습니다 — 먼저 반려(REJECT)하세요"
        }
        current = values
    }

    /** 편집본([current])에 필수값 공란이 남아 있는지 — 승인 차단 판단용(요구사항 §6). */
    fun hasMissingRequired(): Boolean =
        current.requiredValues().any { it.isNullOrBlank() }

    /**
     * 편집 후 재평가 — per-record 3종(누락·규격·단위)을 편집본 기준 [freshPerRecord]로 교체하고,
     * cross-record(중복)는 기존 판정을 유지한다(중복 재평가는 2차 커밋). 값을 고쳐 이상이 해소되면 해당
     * 플래그가 사라지고, 그대로 두면 남는다. ordinal 순으로 정렬해 출력을 결정적으로 유지한다.
     */
    fun reevaluatePerRecordFlags(freshPerRecord: Set<AnomalyRuleBasedFlag>) {
        val keptCrossRecord = flags - AnomalyRule.PER_RECORD_FLAGS
        flags = (freshPerRecord + keptCrossRecord).sortedBy { it.ordinal }.toCollection(LinkedHashSet())
    }

    /**
     * 중복(cross-record) 재평가 반영 — [suspected]면 DUPLICATE_SUSPECTED 추가, 아니면 제거.
     * per-record 플래그는 보존한다. 확정된 형제 레코드는 서비스에서 제외되므로 여기 오지 않는다(변경 금지).
     * 변화가 없으면 아무것도 하지 않아 불필요한 갱신을 피한다.
     */
    fun applyDuplicateSuspected(suspected: Boolean) {
        if ((AnomalyRuleBasedFlag.DUPLICATE_SUSPECTED in flags) == suspected) return
        val next = if (suspected) flags + AnomalyRuleBasedFlag.DUPLICATE_SUSPECTED
        else flags - AnomalyRuleBasedFlag.DUPLICATE_SUSPECTED
        flags = next.sortedBy { it.ordinal }.toCollection(LinkedHashSet())
    }

    /**
     * 확정 — 검수 완료. NEW/REJECTED에서 전이하며, 이미 CONFIRMED면 멱등(상태는 무변화, [memo]는 갱신).
     * 필수값이 누락된 레코드는 확정할 수 없다(먼저 값을 채워야 함).
     */
    fun confirm(memo: String? = null) {
        check(!hasMissingRequired()) {
            "필수값이 누락되어 확정할 수 없습니다 — 먼저 누락 필드를 채우세요"
        }
        status = InspectionRecordStatus.CONFIRMED
        this.memo = memo
    }

    /** 반려 — 다시 손봐야 함. 확정된 레코드의 편집 잠금을 푸는 경로이기도 하다(멱등). [memo]로 사유를 남긴다. */
    fun reject(memo: String? = null) {
        status = InspectionRecordStatus.REJECTED
        this.memo = memo
    }
}
