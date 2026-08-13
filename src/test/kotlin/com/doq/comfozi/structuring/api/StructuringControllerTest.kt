package com.doq.comfozi.structuring.api

import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class StructuringControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val ingestionRepository: IngestionRepository,
) {

    private fun draftWithRecords(): Long =
        service.ingestManual(listOf(manualInput(docId = "A"))).id!!

    @Test
    fun `POST structuring {id} - DRAFT 세션 구조화 + 200 + STRUCTURED 전이`() {
        val id = draftWithRecords()

        mockMvc.perform(post("/api/structuring/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertEquals(IngestionStatus.STRUCTURED, ingestionRepository.findByIdOrNull(id)!!.status)
    }

    @Test
    fun `이미 STRUCTURED 세션 구조화면 409`() {
        val id = draftWithRecords()
        ingestionRepository.save(ingestionRepository.findByIdOrNull(id)!!.apply { markStructured() })

        mockMvc.perform(post("/api/structuring/$id"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }

    @Test
    fun `빈 세션 구조화면 400`() {
        val id = service.createSession().id!! // DRAFT, 행 없음

        mockMvc.perform(post("/api/structuring/$id"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `없는 세션 구조화면 404 + fail envelope`() {
        mockMvc.perform(post("/api/structuring/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
