package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag.DUPLICATE_SUSPECTED
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag.MISSING_REQUIRED
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag.SPEC_MISMATCH
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag.UNIT_MISMATCH
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class InspectionReviewControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val inspectionRepository: InspectionRepository,
    @Autowired val recordRepository: InspectionRecordRepository,
) {

    /** 세션을 구조화해 검수를 만들고 inspectionId를 돌려준다. */
    private fun structured(): Long {
        val session = ingestionService.createFromManualRecords(
            listOf(manualInput(docId = "DOC-1"), manualInput(docId = "DOC-2")),
        )
        structuringService.struct(session.id!!)
        return inspectionRepository.findByIngestionId(session.id!!)!!.id!!
    }

    private fun firstRecordId(inspectionId: Long): Long =
        recordRepository.findByInspectionIdOrderByIdAsc(inspectionId).first().id!!

    @Test
    fun `PATCH record - current 교체`() {
        val recordId = firstRecordId(structured())

        mockMvc.perform(
            patch("/api/inspection/records/$recordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"supplier":"교정공급사","normalizedItemName":"교정품목"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.current.supplier").value("교정공급사"))
            .andExpect(jsonPath("$.data.current.normalizedItemName").value("교정품목"))
            .andExpect(jsonPath("$.data.observed.docId").value("DOC-1")) // 관찰값은 불변

        assertEquals("교정공급사", recordRepository.findById(recordId).get().current.supplier)
    }

    @Test
    fun `PATCH record - 편집으로 이상 해소 시 per-record 플래그 제거`() {
        // 규격+단위 불일치 레코드
        val session = ingestionService.createFromManualRecords(
            listOf(manualInput(docId = "DOC-X", spec = "기존 1kg / 변경 4단", unit = "KG/단")),
        )
        structuringService.struct(session.id!!)
        val inspectionId = inspectionRepository.findByIngestionId(session.id!!)!!.id!!
        val record = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId).first()
        assertEquals(setOf(SPEC_MISMATCH, UNIT_MISMATCH), record.flags) // 초기: 둘 다

        // 단위만 표준값으로 교정(규격은 그대로) → 단위 불일치만 해소
        mockMvc.perform(
            patch("/api/inspection/records/${record.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"docId":"DOC-X","sourceType":"수기","supplier":"직접입력","rawItemName":"임시품목",
                       "spec":"기존 1kg / 변경 4단","unit":"EA","priceBefore":"1000","priceAfter":"1100",
                       "effectiveDate":"2026-08-05","normalizedItemName":"임시품목"}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.flags.length()").value(1))
            .andExpect(jsonPath("$.data.flags[0]").value("SPEC_MISMATCH"))

        assertEquals(setOf(SPEC_MISMATCH), recordRepository.findById(record.id!!).get().flags)
    }

    @Test
    fun `PATCH record - 필수값 비우면 missing_required 추가, 다시 채우면 제거`() {
        val recordId = firstRecordId(structured())

        // 전체 교체로 대부분 필드를 비운다 → 필수값 누락
        mockMvc.perform(
            patch("/api/inspection/records/$recordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawItemName":"품목만"}"""),
        ).andExpect(status().isOk)
        assertEquals(setOf(MISSING_REQUIRED), recordRepository.findById(recordId).get().flags)

        // 온전한 값으로 다시 채움 → 플래그 사라짐
        mockMvc.perform(
            patch("/api/inspection/records/$recordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"docId":"DOC-1","sourceType":"수기","supplier":"직접입력","rawItemName":"임시품목",
                       "spec":"1kg/PK","unit":"PK","priceBefore":"1000","priceAfter":"1100",
                       "effectiveDate":"2026-08-05"}""",
                ),
        ).andExpect(status().isOk)
        assertEquals(emptySet(), recordRepository.findById(recordId).get().flags)
    }

    @Test
    fun `PATCH record - 재평가해도 중복 의심(cross-record)은 유지된다`() {
        // structured()의 DOC-1·DOC-2는 중복키가 같아 DOC-2가 중복 의심
        val inspectionId = structured()
        val dup = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
            .first { DUPLICATE_SUSPECTED in it.flags }

        // per-record 이상(비표준 단위)을 유발하는 편집 → 단위 불일치 추가, 중복은 유지
        mockMvc.perform(
            patch("/api/inspection/records/${dup.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"docId":"DOC-2","sourceType":"수기","supplier":"직접입력","rawItemName":"임시품목",
                       "spec":"1kg/PK","unit":"KG/단","priceBefore":"1000","priceAfter":"1100",
                       "effectiveDate":"2026-08-05"}""",
                ),
        ).andExpect(status().isOk)

        assertEquals(setOf(UNIT_MISMATCH, DUPLICATE_SUSPECTED), recordRepository.findById(dup.id!!).get().flags)
    }

    @Test
    fun `POST record confirm - NEW to CONFIRMED`() {
        val recordId = firstRecordId(structured())

        mockMvc.perform(post("/api/inspection/records/$recordId/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))

        assertEquals(InspectionRecordStatus.CONFIRMED, recordRepository.findById(recordId).get().status)
    }

    @Test
    fun `POST record reject - to REJECTED`() {
        val recordId = firstRecordId(structured())

        mockMvc.perform(post("/api/inspection/records/$recordId/reject"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
    }

    @Test
    fun `PATCH record - 확정된 레코드 편집은 409`() {
        val recordId = firstRecordId(structured())
        mockMvc.perform(post("/api/inspection/records/$recordId/confirm")).andExpect(status().isOk)

        mockMvc.perform(
            patch("/api/inspection/records/$recordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"supplier":"뒤늦은교정"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }

    @Test
    fun `POST inspection confirm - 남은 NEW 일괄 확정`() {
        val inspectionId = structured()
        // 한 건 미리 반려 → 남은 NEW 1건만 확정 대상
        val records = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        mockMvc.perform(post("/api/inspection/records/${records[0].id}/reject")).andExpect(status().isOk)

        mockMvc.perform(post("/api/inspection/$inspectionId/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.inspectionId").value(inspectionId))
            .andExpect(jsonPath("$.data.confirmedCount").value(1))
            .andExpect(jsonPath("$.data.blockedCount").value(0))

        val after = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        assertEquals(InspectionRecordStatus.REJECTED, after[0].status)
        assertEquals(InspectionRecordStatus.CONFIRMED, after[1].status)
    }

    @Test
    fun `POST record confirm - 필수값 누락이면 409 (승인 차단)`() {
        val recordId = firstRecordId(structured())
        // 편집으로 필수값을 비운다(전체 교체 — 대부분 필드 null)
        mockMvc.perform(
            patch("/api/inspection/records/$recordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawItemName":"품목만"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(post("/api/inspection/records/$recordId/confirm"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))

        assertEquals(InspectionRecordStatus.NEW, recordRepository.findById(recordId).get().status)
    }

    @Test
    fun `POST inspection confirm - 필수값 누락 레코드는 건너뛴다`() {
        val inspectionId = structured()
        val records = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        // records[1] 필수값 누락으로 만들기 → 일괄 확정 시 차단(건너뜀)
        mockMvc.perform(
            patch("/api/inspection/records/${records[1].id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawItemName":"품목만"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(post("/api/inspection/$inspectionId/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.confirmedCount").value(1))
            .andExpect(jsonPath("$.data.blockedCount").value(1))

        val after = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        assertEquals(InspectionRecordStatus.CONFIRMED, after[0].status)
        assertEquals(InspectionRecordStatus.NEW, after[1].status) // 차단되어 그대로
    }

    @Test
    fun `POST record confirm - 없는 레코드면 404`() {
        mockMvc.perform(post("/api/inspection/records/999999/confirm"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `POST inspection confirm - 없는 검수면 404`() {
        mockMvc.perform(post("/api/inspection/999999/confirm"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
