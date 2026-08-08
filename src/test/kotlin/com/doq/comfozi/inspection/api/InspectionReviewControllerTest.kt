package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
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

        val after = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        assertEquals(InspectionRecordStatus.REJECTED, after[0].status)
        assertEquals(InspectionRecordStatus.CONFIRMED, after[1].status)
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
