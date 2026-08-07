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

    // 필수 헤더 '적용일' 누락
    private val csvMissingHeader = """
        문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원)
        DOC-001,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,32000,33600
    """.trimIndent()

    // 검증 통과하는 전 필드 수기 레코드 (단가=숫자, 적용일=yyyy-MM-dd)
    private val validManualRecord =
        """{"docId":"MAN-1","sourceType":"수기","supplier":"직접입력","rawItemName":"임시품목","spec":"1kg/PK","unit":"PK","priceBefore":1000,"priceAfter":1100,"effectiveDate":"2026-08-05"}"""

    @Test
    fun `POST uploads - 필수 헤더 누락 파일이면 400 + fail envelope`() {
        val bad = MockMultipartFile("file", "bad.csv", "text/csv", csvMissingHeader.toByteArray())
        mockMvc.perform(multipart("/api/ingestion/uploads").file(bad))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

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
        mockMvc.perform(multipart("/api/ingestion/$id/uploads").file(csvFile()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.ingestionId").value(id))
    }

    @Test
    fun `POST records - 새 DRAFT 세션 생성 + 201`() {
        mockMvc.perform(
            post("/api/ingestion/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[$validManualRecord]"),
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
            post("/api/ingestion/$id/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[$validManualRecord]"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.ingestionId").value(id))
    }

    @Test
    fun `POST records - 잘못된 단가 형식이면 400`() {
        mockMvc.perform(
            post("/api/ingestion/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"docId":"X","priceBefore":"abc"}]"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `POST records - 잘못된 적용일 형식이면 400`() {
        mockMvc.perform(
            post("/api/ingestion/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"docId":"X","effectiveDate":"2026-13-99"}]"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `POST records - 빈 리스트면 400`() {
        mockMvc.perform(
            post("/api/ingestion/records").contentType(MediaType.APPLICATION_JSON).content("[]"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `POST records - 전 필드 공백 레코드면 400`() {
        mockMvc.perform(
            post("/api/ingestion/records").contentType(MediaType.APPLICATION_JSON).content("[{}]"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `없는 세션에 이어붙이면 404 + fail envelope`() {
        mockMvc.perform(
            post("/api/ingestion/999999/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[$validManualRecord]"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `비-DRAFT 세션에 이어붙이면 409 + fail envelope`() {
        val id = ingestionRepository.save(Ingestion(status = IngestionStatus.STRUCTURED)).id!!
        mockMvc.perform(
            post("/api/ingestion/$id/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[$validManualRecord]"),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }
}
