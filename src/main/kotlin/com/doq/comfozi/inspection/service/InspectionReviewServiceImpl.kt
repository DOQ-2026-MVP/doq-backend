package com.doq.comfozi.inspection.service

import com.doq.comfozi.inspection.domain.InspectionChangeLog
import com.doq.comfozi.inspection.domain.InspectionChangeType
import com.doq.comfozi.inspection.domain.InspectionRecord
import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.inspection.domain.diffFields
import com.doq.comfozi.inspection.repository.InspectionChangeLogRepository
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
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
) : InspectionReviewService {

    @Transactional
    override fun edit(recordId: Long, values: MappedRecord): InspectionRecord {
        val record = record(recordId)
        val changes = diffFields(record.current, values) // 이전 편집본 대비 변경분만
        record.edit(values)
        changeLogRepository.save(InspectionChangeLog.edited(record, changes))
        return record
    }

    @Transactional
    override fun confirm(recordId: Long, memo: String?): InspectionRecord =
        transition(recordId, InspectionChangeType.CONFIRM, memo) { it.confirm() }

    @Transactional
    override fun reject(recordId: Long, memo: String?): InspectionRecord =
        transition(recordId, InspectionChangeType.REJECT, memo) { it.reject() }

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
            changeLogRepository.save(InspectionChangeLog.transitioned(record, InspectionChangeType.CONFIRM, from, null))
        }
        return BulkConfirmResult(confirmedCount = confirmable.size, blockedCount = blocked.size)
    }

    /** 상태 전이 공통 — 이전 상태 캡처 → [apply] 전이 → 이력 기록. */
    private fun transition(
        recordId: Long,
        type: InspectionChangeType,
        memo: String?,
        apply: (InspectionRecord) -> Unit,
    ): InspectionRecord {
        val record = record(recordId)
        val from = record.status
        apply(record)
        changeLogRepository.save(InspectionChangeLog.transitioned(record, type, from, memo))
        return record
    }

    private fun record(recordId: Long): InspectionRecord =
        recordRepository.findByIdOrNull(recordId)
            ?: throw NoSuchElementException("알 수 없는 검수 레코드 $recordId")
}
