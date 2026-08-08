package com.doq.comfozi.inspection

import com.doq.comfozi.inspection.domain.Inspection
import com.doq.comfozi.inspection.domain.InspectionRecord
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.structuring.StructuredRecords
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 구조화 결과([StructuredRecords]) 수신 → [Inspection] 1개 + [InspectionRecord] N개 영속 (inspection).
 *
 * 동기(same-tx) 수신 — 계산은 structuring, 여기선 결과 **저장만** 한다(struct 트랜잭션과 원자적으로 커밋).
 * TODO: 추후 디커플링 고려 (@TransactionalEventListener(AFTER_COMMIT) / outbox).
 * TODO(최적화): 레코드는 saveAll이지만 InspectionRecord id가 IDENTITY라 진짜 배치 INSERT는 아님
 *   (행마다 즉시 INSERT). 배치 파일 볼륨이 병목이면 IDENTITY→SEQUENCE + hibernate.jdbc.batch_size로 전환.
 */
@Component
class InspectionIntakeListener(
    private val inspectionRepository: InspectionRepository,
    private val inspectionRecordRepository: InspectionRecordRepository,
) {

    @EventListener
    fun on(event: StructuredRecords) {
        val inspection = inspectionRepository.save(Inspection(ingestionId = event.ingestionId))
        val records = event.records.map { record ->
            InspectionRecord(
                inspectionId = requireNotNull(inspection.id),
                recordId = record.recordId,
                uploadType = record.uploadType,
                rowNo = record.rowNo,
                observed = record.observed,
                current = record.observed.copy(), // 편집본은 독립 사본
                flags = record.flags,
            )
        }
        inspectionRecordRepository.saveAll(records)
    }
}
