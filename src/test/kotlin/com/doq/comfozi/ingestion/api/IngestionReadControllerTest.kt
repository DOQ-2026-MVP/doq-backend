package com.doq.comfozi.ingestion.api

import com.doq.comfozi.ingestion.awaitParsed
import com.doq.comfozi.ingestion.manualInput
import com.doq.comfozi.ingestion.domain.Ingestion
import com.doq.comfozi.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.ingestion.repository.IngestionRepository
import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.ingestion.service.IngestionFileInput
import com.doq.comfozi.ingestion.service.IngestionService
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
 * 세션 조회 — 현황(변경 응답·스트림과 같은 [IngestionState])과, 원본 행 전부를 주는 확인용 경로.
 *
 * 현황에는 화면이 보는 것(올라온 파일들·수기 행들)만 담기고 파일에서 나온 행은 빠진다.
 * 그 행들이 제대로 만들어졌는지는 `/records` 로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionReadControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val uploadRepository: IngestionUploadRepository,
    @Autowired val recordRepository: IngestionRecordRepository,
    @Autowired val ingestionRepository: IngestionRepository,
) {

    private val goldenCsv: ByteArray =
        javaClass.getResourceAsStream("/fixtures/golden-20.csv")!!.readBytes()

    /** 업로드 후 비동기 파싱까지 끝난 세션 id. */
    private fun uploadGolden(): Long {
        val input = IngestionFileInput("golden-20.csv", "text/csv", goldenCsv.inputStream())
        val id = service.ingestFile(input).id!!

        uploadRepository.awaitParsed(id)
        return id
    }

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
            .andExpect(jsonPath("$.data.manuals.length()").value(1))
            .andExpect(jsonPath("$.data.manuals[0].id").exists())
            .andExpect(jsonPath("$.data.manuals[0].createdAt").exists())
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
            .andExpect(jsonPath("$.data.manuals.length()").value(2)) // 파일 20행은 빠진다

        assertEquals(22, recordRepository.findByIngestionIdOrderByIdAsc(id).size) // 행 자체는 다 있다
    }

    @Test
    fun `GET records - 파일 행까지 전부 준다 (현황에서 빠진 것을 확인하는 통로)`() {
        val id = uploadGolden()
        service.ingestManual(listOf(manualInput(docId = "MAN-1")), id)

        mockMvc.perform(get("/api/ingestion/$id/records"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(21)) // 파일 20 + 수기 1
    }

    @Test
    fun `GET records - 파일 행은 원본 근거(업로드·행번호)를 함께 돌려준다`() {
        val id = uploadGolden()

        // DOC-016 = CSV 17행 → 행 목록 16번째
        mockMvc.perform(get("/api/ingestion/$id/records"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[15].content['문서ID']").value("DOC-016"))
            .andExpect(jsonPath("$.data[15].uploadType").value("BATCH_FILE"))
            .andExpect(jsonPath("$.data[15].uploadRowNo").value(17))
            .andExpect(jsonPath("$.data[15].uploadId").value(uploadIdOf(id)))
            .andExpect(jsonPath("$.data[15].createdAt").exists())
    }

    @Test
    fun `GET records - 수기 행은 원본 근거가 없다`() {
        val id = service.ingestManual(listOf(manualInput(docId = "MAN-9"))).id!!

        mockMvc.perform(get("/api/ingestion/$id/records"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].content.docId").value("MAN-9"))
            .andExpect(jsonPath("$.data[0].uploadId").isEmpty)
            .andExpect(jsonPath("$.data[0].uploadType").isEmpty)
            .andExpect(jsonPath("$.data[0].uploadRowNo").isEmpty)

        assertNull(recordRepository.findByIngestionIdOrderByIdAsc(id).single().uploadRef)
    }

    @Test
    fun `GET 없는 세션은 404 - 현황도 행 목록도`() {
        mockMvc.perform(get("/api/ingestion/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))

        // 빈 목록이 아니라 404 여야 한다 — 세션이 없는 것과 행이 없는 것은 다르다
        mockMvc.perform(get("/api/ingestion/999999/records"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `GET 목록은 세션당 한 줄로 업로드·행 수와 상태를 반환한다`() {
        val ingestionId = uploadGolden()

        mockMvc.perform(get("/api/ingestion"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[?(@.ingestionId == %d)].status".format(ingestionId)).value("DRAFT"))
            .andExpect(jsonPath("$.data[?(@.ingestionId == %d)].uploadCount".format(ingestionId)).value(1))
            .andExpect(jsonPath("$.data[?(@.ingestionId == %d)].recordCount".format(ingestionId)).value(20))
            .andExpect(jsonPath("$.data[?(@.ingestionId == %d)].createdAt".format(ingestionId)).isNotEmpty)
    }

    /** 아무것도 안 올라온 세션도 목록에는 나와야 한다 — 0건이라고 사라지면 화면에서 세션을 잃는다. */
    @Test
    fun `GET 목록은 빈 세션도 0으로 세어 포함한다`() {
        val emptyId = ingestionRepository.save(Ingestion()).id!!

        mockMvc.perform(get("/api/ingestion"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[?(@.ingestionId == %d)].uploadCount".format(emptyId)).value(0))
            .andExpect(jsonPath("$.data[?(@.ingestionId == %d)].recordCount".format(emptyId)).value(0))
    }
}
