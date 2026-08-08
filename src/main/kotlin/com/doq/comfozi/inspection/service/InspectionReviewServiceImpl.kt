package com.doq.comfozi.inspection.service

import com.doq.comfozi.inspection.domain.InspectionRecord
import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.structuring.mapping.MappedRecord
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 검수 쓰기 — 상태 전이·편집 규칙은 [InspectionRecord] 도메인에 두고 여기선 조회·오케스트레이션만 한다.
 * 편집본([InspectionRecord.current])·상태 변경은 같은 트랜잭션의 더티체킹으로 영속된다.
 */
@Service
class InspectionReviewServiceImpl(
    private val inspectionRepository: InspectionRepository,
    private val recordRepository: InspectionRecordRepository,
) : InspectionReviewService {

    @Transactional
    override fun edit(recordId: Long, values: MappedRecord): InspectionRecord =
        record(recordId).apply { edit(values) }

    @Transactional
    override fun confirm(recordId: Long): InspectionRecord =
        record(recordId).apply { confirm() }

    @Transactional
    override fun reject(recordId: Long): InspectionRecord =
        record(recordId).apply { reject() }

    @Transactional
    override fun confirmAll(inspectionId: Long): BulkConfirmResult {
        if (!inspectionRepository.existsById(inspectionId)) {
            throw NoSuchElementException("알 수 없는 Inspection $inspectionId")
        }
        val pending = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
            .filter { it.status == InspectionRecordStatus.NEW }
        val (confirmable, blocked) = pending.partition { !it.hasMissingRequired() } // 필수값 누락은 승인 차단
        confirmable.forEach { it.confirm() }
        return BulkConfirmResult(confirmedCount = confirmable.size, blockedCount = blocked.size)
    }

    private fun record(recordId: Long): InspectionRecord =
        recordRepository.findByIdOrNull(recordId)
            ?: throw NoSuchElementException("알 수 없는 검수 레코드 $recordId")
}
