package com.doq.comfozi.inspection.domain

import com.doq.comfozi.structuring.detection.AnomalyRule
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.doq.comfozi.ingestion.domain.IngestionUploadType
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
 * [Inspection]([inspectionId])에 소속된 레코드. 원본 근거는 [ingestionRecordId]·[uploadType]·[rowNo]로 추적.
 *
 * 주의: 이 레코드 자신의 PK는 [id]이고, [ingestionRecordId]는 **다른 테이블(ingestion_record)의 id**다.
 * 레코드 단위 검수 API가 받는 것은 [id]다.
 * - [observed] : 시스템 관찰값(매핑·정규화 결과) — **불변 스냅샷**
 * - [current]  : 검수자가 고치는 **편집본** (초기엔 observed 복사)
 * - [flags]    : detection 이상 플래그(그대로 실어 표시)
 */
@Entity
@Table(name = "inspection_record")
class InspectionRecord(
    @Column(name = "inspection_id", nullable = false, updatable = false)
    val inspectionId: Long,

    @Column(name = "ingestion_record_id", nullable = false, updatable = false)
    val ingestionRecordId: Long,

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

    /** 검수 메모 — 검수자가 남기는 코멘트. 편집([edit])에서만 갱신하고, 상태 전이는 손대지 않는다. */
    @Column(length = 1000)
    var memo: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /**
     * 편집본([current])·[memo] 교체 — 검수자가 관찰값을 교정하고 코멘트를 남긴다.
     * 값과 마찬가지로 메모도 전체 교체다 — [memo]를 비워 보내면 비워진다.
     * 확정된 레코드는 잠겨 있어 편집 불가(먼저 반려해야 함).
     */
    fun edit(values: MappedRecord, memo: String? = null) {
        check(status != InspectionRecordStatus.CONFIRMED) {
            "확정된(CONFIRMED) 레코드는 편집할 수 없습니다 — 먼저 반려(REJECT)하세요"
        }
        current = values
        this.memo = memo
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
     * 확정 — 검수 완료. NEW/REJECTED에서 전이하며, 이미 CONFIRMED면 멱등.
     * 필수값이 누락된 레코드는 확정할 수 없다(먼저 값을 채워야 함). [memo]는 편집의 몫이라 건드리지 않는다.
     */
    fun confirm() {
        check(!hasMissingRequired()) {
            "필수값이 누락되어 확정할 수 없습니다 — 먼저 누락 필드를 채우세요"
        }
        status = InspectionRecordStatus.CONFIRMED
    }

    /** 반려 — 다시 손봐야 함. 확정된 레코드의 편집 잠금을 푸는 경로이기도 하다(멱등). */
    fun reject() {
        status = InspectionRecordStatus.REJECTED
    }

    /**
     * 초기화 — 인계받은 직후(검수 전) 상태로 되돌린다. 편집본을 관찰값으로 되돌리고 메모를 지우며 [NEW][InspectionRecordStatus.NEW]로.
     *
     * 확정·반려 어느 상태에서든 부를 수 있다(편집 잠금과 무관 — 잠금을 푸는 것이 초기화의 일이다).
     * 플래그는 되돌린 값 기준으로 다시 판정해야 하므로 서비스가 재평가한다.
     * 이미 NEW이고 손댄 적 없는 레코드라면 아무것도 바뀌지 않는다(멱등).
     */
    fun reset() {
        status = InspectionRecordStatus.NEW
        current = observed.copy() // 사본으로 — 이후 편집이 불변 스냅샷([observed])까지 건드리면 안 된다
        memo = null
    }
}
