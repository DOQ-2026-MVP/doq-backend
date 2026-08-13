package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.awaitParsed
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 세션 현황 조회 — 변경 응답·현황 스트림과 같은 모델([IngestionState])을 돌려주는가.
 *
 * 화면이 세션에서 보는 것은 올라온 파일들과 수기 행들이라 그만큼만 나간다. 파일에서 나온 행은
 * 응답에 없으므로, 그 행들이 제대로 만들어졌는지는 리포지토리로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionReadControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val uploadRepository: IngestionUploadRepository,
    @Autowired val recordRepository: IngestionRecordRepository,
) {

    private val goldenCsv: ByteArray =
        javaClass.getResourceAsStream("/fixtures/golden-20.csv")!!.readBytes()

    /** 업로드 후 비동기 파싱까지 끝난 세션 id. */
    private fun uploadGolden(): Long = service.ingestFile(
        IngestionFileInput("golden-20.csv", "text/csv", goldenCsv.inputStream()),
    ).id!!.also { uploadRepository.awaitParsed(it) }

    private fun uploadIdOf(ingestionId: Long) =
        uploadRepository.findByIngestionIdOrderByIdAsc(ingestionId).single().id

    @Test
    fun `GET 세션은 수기 행을 id·생성시각과 함께 반환한다`() {
        val id = service.ingestManual(
            listOf(manualInput(docId = "MAN-9", rawItemName = "임시")),
        ).id!!

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.ingestionId").value(id))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.uploads.length()").value(0)) // 수기 입력은 업로드가 아니다
            .andExpect(jsonPath("$.data.manualRecords.length()").value(1))
            .andExpect(jsonPath("$.data.manualRecords[0].id").exists())
            .andExpect(jsonPath("$.data.manualRecords[0].createdAt").exists())
    }

    @Test
    fun `GET 세션은 업로드 현황을 파싱 상태와 함께 반환한다`() {
        val id = uploadGolden()

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.uploads.length()").value(1))
            .andExpect(jsonPath("$.data.uploads[0].type").value("BATCH_FILE"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PARSED"))
            .andExpect(jsonPath("$.data.uploads[0].fileName").value("golden-20.csv"))
            .andExpect(jsonPath("$.data.uploads[0].failureReason").isEmpty)
            .andExpect(jsonPath("$.data.uploads[0].size").value(goldenCsv.size))
    }

    @Test
    fun `파일에서 나온 행은 현황에 담기지 않는다 - 수기 행만 나간다`() {
        val id = uploadGolden()
        service.ingestManual(listOf(manualInput(docId = "MAN-1"), manualInput(docId = "MAN-2")), id)

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.uploads.length()").value(1))
            .andExpect(jsonPath("$.data.manualRecords.length()").value(2)) // 파일 20행은 빠진다

        assertEquals(22, recordRepository.findByIngestionIdOrderByIdAsc(id).size) // 행 자체는 다 있다
    }

    @Test
    fun `파일 행에는 원본 근거(업로드·행번호)가 붙는다`() {
        val id = uploadGolden()

        // DOC-016 = CSV 17행 → 행 목록 16번째
        val record = recordRepository.findByIngestionIdOrderByIdAsc(id)[15]
        assertEquals("DOC-016", record.content.values["문서ID"])
        assertEquals(uploadIdOf(id), record.uploadRef?.uploadId)
        assertEquals(17, record.uploadRef?.rowNo)
    }

    @Test
    fun `수기 행에는 원본 근거가 없다`() {
        val id = service.ingestManual(listOf(manualInput(docId = "MAN-9"))).id!!

        assertNull(recordRepository.findByIngestionIdOrderByIdAsc(id).single().uploadRef)
    }

    @Test
    fun `GET 없는 세션은 404`() {
        mockMvc.perform(get("/api/ingestion/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
