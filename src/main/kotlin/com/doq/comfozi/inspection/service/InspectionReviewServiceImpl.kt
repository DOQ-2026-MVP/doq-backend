package com.doq.comfozi.inspection.service

import com.doq.comfozi.inspection.domain.InspectionChangeLog
import com.doq.comfozi.inspection.domain.InspectionChangeType
import com.doq.comfozi.inspection.domain.InspectionRecord
import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.inspection.domain.diffFields
import com.doq.comfozi.inspection.repository.InspectionChangeLogRepository
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.structuring.detection.AnomalyDetector
import com.doq.comfozi.structuring.mapping.MappedRecord
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 검수 쓰기 — 상태 전이·편집 규칙은 [InspectionRecord] 도메인에 두고 여기선 조회·오케스트레이션·이력 기록만 한다.
 * 편집본([InspectionRecord.current])·상태 변경은 같은 트랜잭션의 더티체킹으로, 변경 이력은 append로 영속된다.
 */
@Service
class InspectionReviewServiceImpl(
    private val inspectionRepository: InspectionRepository,
    private val recordRepository: InspectionRecordRepository,
    private val changeLogRepository: InspectionChangeLogRepository,
    private val anomalyDetector: AnomalyDetector,
) : InspectionReviewService {

    @Transactional
    override fun edit(inspectionRecordId: Long, values: MappedRecord, memo: String?): InspectionRecord {
        val record = record(inspectionRecordId)
        val changes = diffFields(record.current, values) // 이전 편집본 대비 변경분만
        record.edit(values, memo)
        reevaluateFlags(record)
        changeLogRepository.save(InspectionChangeLog.edited(record, changes))
        return record
    }

    @Transactional
    override fun reset(inspectionRecordId: Long): InspectionRecord {
        val record = record(inspectionRecordId)
        val from = record.status
        val changes = diffFields(record.current, record.observed) // 되돌리며 사라지는 교정분
        record.reset()
        reevaluateFlags(record) // 관찰값으로 돌아갔으니 플래그도 인계 직후 판정으로 되돌아간다
        changeLogRepository.save(InspectionChangeLog.reset(record, from, changes))
        return record
    }

    /** 값이 바뀐 뒤의 플래그 재평가 — 자신은 per-record(누락·규격·단위), 세션 전체는 중복(cross-record). */
    private fun reevaluateFlags(record: InspectionRecord) {
        record.reevaluatePerRecordFlags(anomalyDetector.detectPerRecord(record.current))
        reevaluateDuplicates(record.inspectionId) // 한 건의 변경이 형제의 중복 여부까지 바꾼다
    }

    /**
     * 세션 전체 중복 재평가 — 형제 레코드들의 현재값으로 중복 그룹을 다시 판정해 DUPLICATE_SUSPECTED를
     * add/clear한다. 확정(CONFIRMED) 레코드는 매칭 후보로는 포함하되(현재값 참여) 플래그는 바꾸지 않는다.
     */
    private fun reevaluateDuplicates(inspectionId: Long) {
        val siblings = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        val duplicates = anomalyDetector.detectDuplicates(siblings.map { it.current })
        siblings.forEachIndexed { i, sibling ->
            if (sibling.status == InspectionRecordStatus.CONFIRMED) return@forEachIndexed // 변경 금지
            sibling.applyDuplicateSuspected(i in duplicates)
        }
    }

    @Transactional
    override fun confirm(inspectionRecordId: Long): InspectionRecord =
        transition(inspectionRecordId, InspectionChangeType.CONFIRM) { it.confirm() }

    @Transactional
    override fun reject(inspectionRecordId: Long): InspectionRecord =
        transition(inspectionRecordId, InspectionChangeType.REJECT) { it.reject() }

    @Transactional
    override fun confirmAll(inspectionId: Long): BulkConfirmResult {
        if (!inspectionRepository.existsById(inspectionId)) {
            throw NoSuchElementException("알 수 없는 Inspection $inspectionId")
        }
        val pending = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
            .filter { it.status == InspectionRecordStatus.NEW }
        val (confirmable, blocked) = pending.partition { !it.hasMissingRequired() } // 필수값 누락은 승인 차단
        confirmable.forEach { record ->
            val from = record.status
            record.confirm()
            changeLogRepository.save(InspectionChangeLog.transitioned(record, InspectionChangeType.CONFIRM, from))
        }
        return BulkConfirmResult(confirmedCount = confirmable.size, blockedCount = blocked.size)
    }

    /** 상태 전이 공통 — 이전 상태 캡처 → [apply] 전이 → 이력 기록. */
    private fun transition(
        inspectionRecordId: Long,
        type: InspectionChangeType,
        apply: (InspectionRecord) -> Unit,
    ): InspectionRecord {
        val record = record(inspectionRecordId)
        val from = record.status
        apply(record)
        changeLogRepository.save(InspectionChangeLog.transitioned(record, type, from))
        return record
    }

    private fun record(inspectionRecordId: Long): InspectionRecord =
        recordRepository.findByIdOrNull(inspectionRecordId)
            ?: throw NoSuchElementException("알 수 없는 검수 레코드 $inspectionRecordId")
}
