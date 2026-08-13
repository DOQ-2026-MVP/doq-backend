package com.doq.comfozi.inspection

import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.doq.comfozi.structuring.awaitInspection
import com.doq.comfozi.ingestion.manualInput
import com.doq.comfozi.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * struct → inspection 인계가 Inspection 1개 + InspectionRecord N개로 영속되는지 (동기 same-tx) 확인.
 * jsonb(MappedRecord·flags) 저장/복원 왕복도 함께 검증한다.
 */
@SpringBootTest
class InspectionIntakeTest(
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val inspectionRepository: InspectionRepository,
    @Autowired val inspectionRecordRepository: InspectionRecordRepository,
) {

    @Test
    fun `struct 결과가 Inspection 1개 + InspectionRecord N개로 영속된다`() {
        val session = ingestionService.ingestManual(
            listOf(manualInput(docId = "DOC-1"), manualInput(docId = "DOC-2")),
        )

        structuringService.struct(session.id!!)

        val inspection = inspectionRepository.awaitInspection(session.id!!)
        assertEquals(session.id, inspection.ingestionId)

        val records = inspectionRecordRepository.findByInspectionIdOrderByIdAsc(inspection.id!!)
        assertEquals(2, records.size)

        val record = records.first()
        assertEquals(inspection.id, record.inspectionId)
        assertEquals("DOC-1", record.observed.docId)
        assertEquals("DOC-1", record.current.docId) // 편집본 = observed 복사
        assertEquals(InspectionRecordStatus.NEW, record.status)
        // flags jsonb 왕복: 두 레코드는 중복이라 2번째(DOC-2)에 duplicate 플래그
        assertEquals(setOf(AnomalyRuleBasedFlag.DUPLICATE_SUSPECTED), records[1].flags)
    }
}
