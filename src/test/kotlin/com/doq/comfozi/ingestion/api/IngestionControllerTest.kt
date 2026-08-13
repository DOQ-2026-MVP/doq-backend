package com.doq.comfozi.ingestion.api

import com.doq.comfozi.ingestion.awaitParsed
import com.doq.comfozi.ingestion.domain.Ingestion
import com.doq.comfozi.ingestion.domain.IngestionStatus
import com.doq.comfozi.ingestion.repository.IngestionRepository
import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
    @Autowired val uploadRepository: IngestionUploadRepository,
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
    fun `POST uploads - 지원하지 않는 형식이면 400 + fail envelope`() {
        // 분류는 동기라 저장 전에 걸린다 (헤더 누락 등 파싱 단계 실패는 비동기 → PARSE_FAILED 상태)
        val bad = MockMultipartFile("file", "x.bin", "application/octet-stream", byteArrayOf(0x00, 0x01, 0x02))
        mockMvc.perform(multipart("/api/ingestion/uploads").file(bad))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `POST uploads - 필수 헤더 누락 파일도 접수는 되고 파싱 단계에서 실패한다`() {
        val bad = MockMultipartFile("file", "bad.csv", "text/csv", csvMissingHeader.toByteArray())
        val body = mockMvc.perform(multipart("/api/ingestion/uploads").file(bad))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val id = Regex("\"ingestionId\":(\\d+)").find(body)!!.groupValues[1].toLong()
        uploadRepository.awaitParsed(id)

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PARSE_FAILED"))
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
    fun `POST uploads - 응답은 바뀐 뒤 현황이다 (스트림이 흘리는 것과 같은 모델)`() {
        mockMvc.perform(multipart("/api/ingestion/uploads").file(csvFile()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.uploads.length()").value(1))
            .andExpect(jsonPath("$.data.uploads[0].fileName").value("test.csv"))
            .andExpect(jsonPath("$.data.manuals.length()").value(0))
        // 상태값은 단언하지 않는다 — 파싱이 커밋 직후 비동기로 돌아 PARSING/PARSED 어느 쪽도 가능하다
    }

    @Test
    fun `POST records - 응답 현황에 저장된 수기 행이 id·생성시각과 함께 담긴다`() {
        mockMvc.perform(
            post("/api/ingestion/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[$validManualRecord]"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.uploads.length()").value(0))
            .andExpect(jsonPath("$.data.manuals.length()").value(1))
            .andExpect(jsonPath("$.data.manuals[0].id").exists())
            .andExpect(jsonPath("$.data.manuals[0].createdAt").exists())
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
