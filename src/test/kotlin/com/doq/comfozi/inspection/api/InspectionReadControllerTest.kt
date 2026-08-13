package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.ingestion.manualInput
import com.doq.comfozi.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
class InspectionReadControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val inspectionRepository: InspectionRepository,
) {

    /** 세션 하나를 구조화해 검수를 만들고 (ingestionId, inspectionId)를 돌려준다. */
    private fun structured(): Pair<Long, Long> {
        val session = ingestionService.ingestManual(
            listOf(manualInput(docId = "DOC-1"), manualInput(docId = "DOC-2")),
        )
        structuringService.struct(session.id!!)
        val inspection = inspectionRepository.findByIngestionId(session.id!!)!!
        return session.id!! to inspection.id!!
    }

    @Test
    fun `GET inspection {id} - inspectionId로 상세 + 레코드`() {
        val (_, inspectionId) = structured()

        mockMvc.perform(get("/api/inspection/$inspectionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.inspectionId").value(inspectionId))
            .andExpect(jsonPath("$.data.records.length()").value(2))
            .andExpect(jsonPath("$.data.records[0].observed.docId").value("DOC-1"))
            .andExpect(jsonPath("$.data.records[0].status").value("NEW"))
    }

    @Test
    fun `GET inspection - ingestionId로 상세 + 레코드`() {
        val (ingestionId, inspectionId) = structured()

        mockMvc.perform(get("/api/inspection").param("ingestionId", ingestionId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.inspectionId").value(inspectionId))
            .andExpect(jsonPath("$.data.ingestionId").value(ingestionId))
            .andExpect(jsonPath("$.data.records.length()").value(2))
    }

    @Test
    fun `GET inspection {id} - 없으면 404`() {
        mockMvc.perform(get("/api/inspection/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `GET inspection - 없는 ingestionId면 404`() {
        mockMvc.perform(get("/api/inspection").param("ingestionId", "999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
