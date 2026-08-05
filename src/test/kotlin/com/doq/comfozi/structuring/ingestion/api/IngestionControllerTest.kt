package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val ingestionRepository: IngestionRepository,
) {

    private val csv = """
        문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원),적용일
        DOC-001,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,32000,33600,2026-08-01
    """.trimIndent()

    private fun csvFile() = MockMultipartFile("file", "test.csv", "text/csv", csv.toByteArray())

    @Test
    fun `POST uploads - 새 DRAFT 세션 생성 + 201`() {
        mockMvc.perform(multipart("/api/ingestion/uploads").file(csvFile()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.ingestionId").exists())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
    }

    @Test
    fun `POST uploads {id} - 기존 세션에 이어붙임 + 201`() {
        val id = service.createSession().id!!
        mockMvc.perform(multipart("/api/ingestion/uploads/$id").file(csvFile()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.ingestionId").value(id))
    }

    @Test
    fun `POST records - 새 DRAFT 세션 생성 + 201`() {
        mockMvc.perform(
            post("/api/ingestion/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"docId":"MAN-1","rawItemName":"임시품목","unit":"PK"}]"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.ingestionId").exists())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
    }

    @Test
    fun `POST records {id} - 기존 세션에 이어붙임 + 201`() {
        val id = service.createSession().id!!
        mockMvc.perform(
            post("/api/ingestion/records/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"docId":"MAN-2","rawItemName":"임시품목2"}]"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.ingestionId").value(id))
    }

    @Test
    fun `없는 세션에 이어붙이면 404 + fail envelope`() {
        mockMvc.perform(
            post("/api/ingestion/records/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"docId":"X"}]"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `비-DRAFT 세션에 이어붙이면 409 + fail envelope`() {
        val id = ingestionRepository.save(Ingestion(status = IngestionStatus.PARSED)).id!!
        mockMvc.perform(
            post("/api/ingestion/records/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"docId":"X"}]"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }
}
