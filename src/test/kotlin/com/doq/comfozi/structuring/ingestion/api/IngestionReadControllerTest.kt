package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.structuring.ingestion.service.IngestionFileInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionReadControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val uploadRepository: IngestionUploadRepository,
) {

    private val goldenCsv: ByteArray =
        javaClass.getResourceAsStream("/fixtures/golden-20.csv")!!.readBytes()

    private fun uploadGolden() = service.createFromFile(
        IngestionFileInput("golden-20.csv", "text/csv", goldenCsv.inputStream()),
    ).id!!

    private fun uploadIdOf(ingestionId: Long) =
        uploadRepository.findByIngestionIdOrderByIdAsc(ingestionId).single().id

    @Test
    fun `GET 세션은 세션과 원본 행들을 반환한다`() {
        val id = service.createFromManualRecords(
            listOf(manualInput(docId = "MAN-9", rawItemName = "임시")),
        ).id!!

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.ingestionId").value(id))
            .andExpect(jsonPath("$.data.records[0].content.docId").value("MAN-9"))
    }

    @Test
    fun `GET 세션은 업로드 현황을 파싱 상태·행 수와 함께 반환한다`() {
        val id = uploadGolden()

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.uploads.length()").value(1))
            .andExpect(jsonPath("$.data.uploads[0].type").value("BATCH_FILE"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PARSED"))
            .andExpect(jsonPath("$.data.uploads[0].fileName").value("golden-20.csv"))
            .andExpect(jsonPath("$.data.uploads[0].recordCount").value(20))
            .andExpect(jsonPath("$.data.uploads[0].size").value(goldenCsv.size))
    }

    @Test
    fun `업로드 행 수는 수기 입력을 세지 않는다`() {
        val id = uploadGolden()
        service.continueFromManualRecords(id, listOf(manualInput(docId = "MAN-1"), manualInput(docId = "MAN-2")))

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.records.length()").value(22))
            .andExpect(jsonPath("$.data.uploads[0].recordCount").value(20)) // 수기 2건 제외
    }

    @Test
    fun `파일 행은 원본 근거(업로드·행번호)를 함께 돌려준다`() {
        val id = uploadGolden()

        // DOC-016 = CSV 17행 → 행 목록 16번째
        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.records[15].content['문서ID']").value("DOC-016"))
            .andExpect(jsonPath("$.data.records[15].uploadType").value("BATCH_FILE"))
            .andExpect(jsonPath("$.data.records[15].uploadRowNo").value(17))
            .andExpect(jsonPath("$.data.records[15].uploadId").value(uploadIdOf(id)))
    }

    @Test
    fun `수기 행은 업로드도 원본 근거도 없다`() {
        val id = service.createFromManualRecords(listOf(manualInput(docId = "MAN-9"))).id!!

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.uploads.length()").value(0)) // 수기는 업로드가 아니다
            .andExpect(jsonPath("$.data.records[0].uploadId").isEmpty)
            .andExpect(jsonPath("$.data.records[0].uploadType").isEmpty)
            .andExpect(jsonPath("$.data.records[0].uploadRowNo").isEmpty)
    }

    @Test
    fun `GET 없는 세션은 404`() {
        mockMvc.perform(get("/api/ingestion/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
