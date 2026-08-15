package com.doq.comfozi.ingestion.api

import com.doq.comfozi.ingestion.awaitParsed
import com.doq.comfozi.ingestion.domain.IngestionUploadStatus
import com.doq.comfozi.ingestion.domain.IngestionUploadType
import com.doq.comfozi.ingestion.extraction.ExtractedItem
import com.doq.comfozi.ingestion.extraction.ItemExtractor
import com.doq.comfozi.ingestion.extraction.TestPdf
import com.doq.comfozi.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.awaitInspection
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import org.hamcrest.Matchers.containsString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PDF 원본 → 항목 추출 (추가 요건) 전 경로 — 업로드부터 검수 인박스까지.
 *
 * 추출기를 페이크로 대체해 **실제 LLM 호출 없이** 검증한다. 제공된 원본 PDF 는
 * `docs/requirements/` 가 gitignore 라 픽스처로 쓸 수 없어 [TestPdf] 로 만들어 쓴다
 * (실제 원본 회귀는 로컬 수동 검증).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionPdfExtractionTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val structuringService: StructuringService,
    @Autowired val uploadRepository: IngestionUploadRepository,
    @Autowired val recordRepository: IngestionRecordRepository,
    @Autowired val inspectionRepository: InspectionRepository,
    @Autowired val inspectionRecordRepository: InspectionRecordRepository,
    @Autowired val fakeExtractor: FakeItemExtractor,
) {

    /** 문서 텍스트 대신 미리 정한 항목을 돌려주는 추출기 — 실패도 재현한다. */
    class FakeItemExtractor : ItemExtractor {
        var items: List<ExtractedItem> = emptyList()
        var failure: String? = null

        override fun extract(fileName: String, text: String): List<ExtractedItem> {
            failure?.let { throw IllegalArgumentException(it) }
            return items
        }
    }

    @TestConfiguration
    class Config {
        @Bean
        fun fakeItemExtractor() = FakeItemExtractor()
    }

    private val pdf = TestPdf.of("PRICE CHANGE NOTICE", "Tomato Salsa 4kg/PK 32000 -> 33600")

    // 페이크는 컨텍스트에 하나뿐이라 테스트마다 되돌린다 (안 하면 앞 테스트의 실패 설정이 샌다)
    @BeforeTest
    fun reset() {
        fakeExtractor.items = emptyList()
        fakeExtractor.failure = null
    }

    private fun item(name: String, before: String = "32000", after: String = "33600") = ExtractedItem(
        supplier = "가온푸드",
        rawItemName = name,
        spec = "4kg/PK",
        unit = "PK",
        priceBefore = before,
        priceAfter = after,
        effectiveDate = "2026-08-01",
    )

    /** PDF 를 올리고 비동기 추출이 끝나면 세션 id. */
    private fun upload(fileName: String = "단가변경공문.pdf"): Long {
        val file = MockMultipartFile("file", fileName, "application/pdf", pdf)
        val body = mockMvc.perform(multipart("/api/ingestion/uploads").file(file))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val id = Regex("\"ingestionId\":(\\d+)").find(body)!!.groupValues[1].toLong()

        uploadRepository.awaitParsed(id)
        return id
    }

    @Test
    fun `PDF 한 장에서 여러 항목이 나오고 각각 문서 안 순번을 갖는다`() {
        fakeExtractor.items = listOf(item("토마토살사S/O"), item("할라피뇨슬라이스"))

        val id = upload()

        val upload = uploadRepository.findByIngestionIdOrderByIdAsc(id).single()
        assertEquals(IngestionUploadType.FILE, upload.type)
        assertEquals(IngestionUploadStatus.PARSED, upload.status)

        val records = recordRepository.findByIngestionIdOrderByIdAsc(id)
        assertEquals(2, records.size)
        assertEquals(listOf(1, 2), records.map { it.uploadRef?.rowNo }) // 같은 파일, 순번으로 구분
        assertTrue(records.all { it.uploadRef?.uploadType == IngestionUploadType.FILE })
        assertEquals("토마토살사S/O", records[0].content.values["rawItemName"])
    }

    @Test
    fun `문서ID·원본유형은 본문에 없으므로 시스템이 부여한다 (추출 대상이 아니다)`() {
        fakeExtractor.items = listOf(item("토마토살사S/O"), item("할라피뇨슬라이스"))

        val id = upload()
        val uploadId = uploadRepository.findByIngestionIdOrderByIdAsc(id).single().id
        val records = recordRepository.findByIngestionIdOrderByIdAsc(id)

        assertEquals(
            listOf("DOC-U$uploadId-1", "DOC-U$uploadId-2"),
            records.map { it.content.values["docId"] },
        )
        assertTrue(records.all { it.content.values["sourceType"] == "PDF" })
    }

    @Test
    fun `부여한 덕분에 필수값 누락으로 떨어지지 않는다`() {
        fakeExtractor.items = listOf(item("토마토살사S/O"))
        val id = upload()

        structuringService.struct(id)

        val inspection = inspectionRepository.awaitInspection(id)
        val record = inspectionRecordRepository.findByInspectionIdOrderByIdAsc(inspection.id!!).single()
        assertEquals(false, record.flags.contains(AnomalyRuleBasedFlag.MISSING_REQUIRED))
    }

    @Test
    fun `export 에 파일명과 문서 안 위치가 원본 근거로 실린다`() {
        fakeExtractor.items = listOf(item("토마토살사S/O"))
        val id = upload("가온푸드_단가변경공문.pdf")
        structuringService.struct(id)

        val inspection = inspectionRepository.awaitInspection(id)
        val inspectionRecordId = inspectionRecordRepository.findByInspectionIdOrderByIdAsc(inspection.id!!).single().id
        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/confirm")).andExpect(status().isOk)

        mockMvc.perform(get("/api/inspection/${inspection.id}/export.json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].source_ref.input_method").value("file"))
            .andExpect(jsonPath("$[0].source_ref.file_name").value("가온푸드_단가변경공문.pdf"))
            .andExpect(jsonPath("$[0].source_ref.row_no").value(1))
    }

    @Test
    fun `추출에 실패하면 사유와 함께 PARSE_FAILED 로 남고 행은 없다`() {
        fakeExtractor.failure = "표를 해석하지 못했습니다"

        val id = upload()

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PARSE_FAILED"))
            .andExpect(jsonPath("$.data.uploads[0].failureReason").value(containsString("표를 해석하지 못했습니다")))
        assertTrue(recordRepository.findByIngestionIdOrderByIdAsc(id).isEmpty())
    }

    @Test
    fun `추출 항목에 docId 를 지어내라고 시키지 않는다`() {
        // ExtractedItem 에 docId 필드가 없어서 모델이 값을 넣을 자리 자체가 없다
        assertEquals(
            false,
            ExtractedItem::class.java.declaredFields.any { it.name == "docId" || it.name == "sourceType" },
        )
    }

    @Test
    fun `항목이 0건이어도 실패가 아니라 완료다`() {
        fakeExtractor.items = emptyList()

        val id = upload()

        assertEquals(IngestionUploadStatus.PARSED, uploadRepository.findByIngestionIdOrderByIdAsc(id).single().status)
        assertTrue(recordRepository.findByIngestionIdOrderByIdAsc(id).isEmpty())
    }
}
