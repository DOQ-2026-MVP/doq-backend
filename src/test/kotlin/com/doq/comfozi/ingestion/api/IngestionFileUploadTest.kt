package com.doq.comfozi.ingestion.api

import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.ingestion.awaitParsed
import com.doq.comfozi.ingestion.domain.IngestionUploadStatus
import com.doq.comfozi.ingestion.extraction.TestPdf
import com.doq.comfozi.ingestion.manualInput
import com.doq.comfozi.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.ingestion.service.IngestionService
import org.hamcrest.Matchers.containsString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합 업로드 엔드포인트(`POST /uploads`) — 표 파일이든 원본 문서든 같은 입구로 받고
 * **내용으로** 처리 경로가 갈리는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionFileUploadTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val uploadRepository: IngestionUploadRepository,
    @Autowired val recordRepository: IngestionRecordRepository,
) {

    // PDF 는 실제로 열어 텍스트를 뽑으므로 진짜 PDF 여야 한다 (이 컨텍스트엔 LLM 추출기가 없다).
    // 표가 아닌 안내문이라 규칙 파서가 행을 못 잡고 원문 하락 경로를 탄다.
    private val pdfBytes = TestPdf.of("PRICE CHANGE NOTICE", "Please contact our sales team.")
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + "png".toByteArray()
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + "jpg".toByteArray()

    private val csv = """
        문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원),적용일
        DOC-001,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,32000,33600,2026-08-01
    """.trimIndent().toByteArray()

    private fun file(name: String, type: String, bytes: ByteArray) = MockMultipartFile("file", name, type, bytes)
    private fun pdf() = file("증빙.pdf", "application/pdf", pdfBytes)

    /** 새 세션에 올리고 비동기 파싱까지 끝난 뒤 세션 id 를 돌려준다. */
    private fun upload(f: MockMultipartFile): Long {
        val body = mockMvc.perform(multipart("/api/ingestion/uploads").file(f))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val id = Regex("\"ingestionId\":(\\d+)").find(body)!!.groupValues[1].toLong()
        uploadRepository.awaitParsed(id)
        return id
    }

    @Test
    fun `표를 못 읽은 PDF 는 원문이 한 행으로 담긴다 - 검수에서 보완하도록`() {
        val id = upload(pdf())

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.uploads.length()").value(1))
            .andExpect(jsonPath("$.data.uploads[0].type").value("FILE"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PARSED"))
            .andExpect(jsonPath("$.data.uploads[0].fileName").value("증빙.pdf"))
            .andExpect(jsonPath("$.data.manuals.length()").value(0)) // 파일 출처라 수기 목록엔 없다

        // 읽어낸 원문이 품목명 자리에 담기고, 나머지는 비어 필수값 누락으로 검수에 올라간다
        val record = recordRepository.findByIngestionIdOrderByIdAsc(id).single()
        assertTrue(record.content.values["rawItemName"]!!.contains("PRICE CHANGE NOTICE"))
        assertEquals("PDF", record.content.values["sourceType"])
        assertNull(record.content.values["supplier"])
    }

    @Test
    fun `제공된 실제 공문은 LLM 없이도 항목별로 갈려 적재된다`() {
        val real = javaClass.getResourceAsStream("/fixtures/notice-gaonfood.pdf")!!.readBytes()

        val id = upload(file("가온푸드_단가변경공문.pdf", "application/pdf", real))

        val records = recordRepository.findByIngestionIdOrderByIdAsc(id)
        assertEquals(3, records.size) // 한 문서에서 여러 항목
        assertEquals(listOf(1, 2, 3), records.map { it.uploadRef?.rowNo })

        val first = records.first().content.values
        assertEquals("토마토살사S/O", first["rawItemName"])
        assertEquals("가온푸드", first["supplier"])
        assertEquals("33,600", first["priceAfter"])
        assertEquals("PDF", first["sourceType"]) // 시스템 부여
        assertTrue(first["docId"]!!.startsWith("DOC-U")) // 시스템 채번
    }

    @Test
    fun `이미지에서 글자를 못 읽으면 실패로 남는다 - OCR 이 있을 때`() {
        // 매직바이트만 맞춘 가짜 PNG — OCR 이 있으면 "글자 없음"으로 실패, 없으면 행 0건
        val id = upload(file("a.png", "image/png", pngBytes))

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].type").value("FILE"))
        assertTrue(recordRepository.findByIngestionIdOrderByIdAsc(id).isEmpty())
    }

    @Test
    fun `같은 엔드포인트에 표 파일을 올리면 파싱 경로로 간다`() {
        val id = upload(file("golden.csv", "text/csv", csv))

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].type").value("BATCH_FILE"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PARSED"))

        // 파일 행은 현황에 안 실린다 — 적재됐는지는 저장된 행으로 본다
        val records = recordRepository.findByIngestionIdOrderByIdAsc(id)
        assertEquals("DOC-001", records.single().content.values["문서ID"])
    }

    @Test
    fun `한 세션에 표 파일과 원본 문서를 섞어 올릴 수 있다`() {
        val id = upload(file("golden.csv", "text/csv", csv))
        mockMvc.perform(multipart("/api/ingestion/$id/uploads").file(pdf())).andExpect(status().isCreated)
        uploadRepository.awaitParsed(id)

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads.length()").value(2))
            .andExpect(jsonPath("$.data.uploads[0].type").value("BATCH_FILE"))
            .andExpect(jsonPath("$.data.uploads[1].type").value("FILE"))

        // 표 파일 1행 + PDF 원문 1행
        assertEquals(2, recordRepository.findByIngestionIdOrderByIdAsc(id).size)
    }

    @Test
    fun `확장자는 보지 않는다 - 확장자 없는 CSV도 파싱된다`() {
        val id = upload(file("noext", "application/octet-stream", csv))

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].type").value("BATCH_FILE"))

        assertEquals(1, recordRepository.findByIngestionIdOrderByIdAsc(id).size)
    }

    @Test
    fun `확장자는 보지 않는다 - csv 확장자를 단 PDF는 보관 경로로 간다`() {
        val id = upload(file("가짜.csv", "text/csv", pdfBytes))

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].type").value("FILE"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PARSED"))
    }

    @Test
    fun `임의 바이너리는 400 - CSV 파서까지 흘러가지 않는다`() {
        val binary = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7F, 0x00)

        mockMvc.perform(multipart("/api/ingestion/uploads").file(file("x.bin", "application/octet-stream", binary)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.message").value(containsString("지원하지 않는 파일 형식")))
    }

    @Test
    fun `zip 이지만 xlsx 가 아니면 파싱 실패로 남는다 - 스택트레이스 대신 안내`() {
        val docxLike = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(64) { 0x41 }

        // 매직 바이트로는 xlsx 와 구분되지 않으므로 접수는 되고, 파싱 단계에서 걸린다
        val id = upload(file("문서.docx", "application/msword", docxLike))

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PARSE_FAILED"))
            .andExpect(jsonPath("$.data.uploads[0].failureReason").value(containsString("읽을 수 없습니다")))

        assertTrue(recordRepository.findByIngestionIdOrderByIdAsc(id).isEmpty())
    }

    @Test
    fun `텍스트지만 헤더가 틀리면 헤더 누락으로 안내한다`() {
        val wrongHeader = "이름,수량\n연필,3".toByteArray()

        val id = upload(file("other.csv", "text/csv", wrongHeader))

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PARSE_FAILED"))
            .andExpect(jsonPath("$.data.uploads[0].failureReason").value(containsString("필수 헤더 누락")))
    }

    @Test
    fun `파싱에 실패해도 원본은 남는다 - 확인 후 지울 수 있다`() {
        val wrongHeader = "이름,수량\n연필,3".toByteArray()
        val id = upload(file("other.csv", "text/csv", wrongHeader))
        val uploadId = uploadRepository.findByIngestionIdOrderByIdAsc(id).single().id!!

        mockMvc.perform(get("/api/ingestion/$id/uploads/$uploadId/content"))
            .andExpect(status().isOk)
            .andExpect(content().bytes(wrongHeader))

        mockMvc.perform(delete("/api/ingestion/$id/uploads/$uploadId")).andExpect(status().isOk)
        assertTrue(uploadRepository.findByIngestionIdOrderByIdAsc(id).isEmpty())
    }

    @Test
    fun `파싱이 끝나지 않은 세션은 구조화할 수 없다`() {
        val id = upload(file("golden.csv", "text/csv", csv))
        val upload = uploadRepository.findByIngestionIdOrderByIdAsc(id).single()
        uploadRepository.save(upload.apply { status = IngestionUploadStatus.PARSING }) // 파싱 중 재현

        assertFailsWith<IllegalStateException> { structuringService.struct(id) }
    }

    @Test
    fun `업로드 실패는 부작용을 남기지 않는다`() {
        val before = uploadRepository.count()

        val bad = file("x.bin", "application/octet-stream", byteArrayOf(0x00, 0x01))
        mockMvc.perform(multipart("/api/ingestion/uploads").file(bad))
            .andExpect(status().isBadRequest)

        assertEquals(before, uploadRepository.count())
    }

    @Test
    fun `기존 세션에 이어붙는다`() {
        val id = service.ingestManual(listOf(manualInput(docId = "MAN-1"))).id!!

        mockMvc.perform(multipart("/api/ingestion/$id/uploads").file(pdf())).andExpect(status().isCreated)

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads.length()").value(1))
            .andExpect(jsonPath("$.data.manuals.length()").value(1)) // 수기 행은 그대로
    }

    @Test
    fun `GET 업로드 원본 - 올린 바이트를 그대로 돌려준다`() {
        val id = upload(pdf())
        val uploadId = uploadRepository.findByIngestionIdOrderByIdAsc(id).first().id!!

        mockMvc.perform(get("/api/ingestion/$id/uploads/$uploadId/content"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", containsString("inline")))
            .andExpect(content().bytes(pdfBytes))
    }

    @Test
    fun `GET 업로드 원본 - 다른 세션의 업로드면 404`() {
        val mine = upload(pdf())
        val other = upload(pdf())
        val othersUpload = uploadRepository.findByIngestionIdOrderByIdAsc(other).first().id!!

        mockMvc.perform(get("/api/ingestion/$mine/uploads/$othersUpload/content"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `행을 못 만드는 원본 문서만 있는 세션은 구조화할 것이 없어 400`() {
        val id = upload(file("a.png", "image/png", pngBytes)) // 이미지는 읽어낼 텍스트가 없다

        assertFailsWith<IllegalArgumentException> { structuringService.struct(id) }
    }

    @Test
    fun `원본 문서 업로드도 업로드 단위 삭제가 된다`() {
        val id = upload(pdf())
        val uploadId = uploadRepository.findByIngestionIdOrderByIdAsc(id).first().id!!

        mockMvc.perform(delete("/api/ingestion/$id/uploads/$uploadId")).andExpect(status().isOk)

        assertEquals(0, uploadRepository.findByIngestionIdOrderByIdAsc(id).size)
    }
}
