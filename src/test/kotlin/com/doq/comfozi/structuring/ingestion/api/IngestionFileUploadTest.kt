package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.structuring.ingestion.service.IngestionService
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

    // 보관 경로는 매직 바이트까지만 보므로 뒤 내용은 파싱되지 않는다.
    private val pdfBytes = "%PDF-1.7\n증빙 원본".toByteArray()
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + "png".toByteArray()
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + "jpg".toByteArray()

    private val csv = """
        문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원),적용일
        DOC-001,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,32000,33600,2026-08-01
    """.trimIndent().toByteArray()

    private fun file(name: String, type: String, bytes: ByteArray) = MockMultipartFile("file", name, type, bytes)
    private fun pdf() = file("증빙.pdf", "application/pdf", pdfBytes)

    private fun upload(f: MockMultipartFile): Long {
        val body = mockMvc.perform(multipart("/api/ingestion/uploads").file(f))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return Regex("\"ingestionId\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    @Test
    fun `원본 문서는 보관되고 행은 만들어지지 않는다`() {
        val id = upload(pdf())

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.uploads.length()").value(1))
            .andExpect(jsonPath("$.data.uploads[0].type").value("FILE"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PENDING_EXTRACTION"))
            .andExpect(jsonPath("$.data.uploads[0].fileName").value("증빙.pdf"))
            .andExpect(jsonPath("$.data.uploads[0].recordCount").value(0))
            .andExpect(jsonPath("$.data.records.length()").value(0))

        assertTrue(recordRepository.findByIngestionIdOrderByIdAsc(id).isEmpty())
    }

    @Test
    fun `PNG·JPEG도 보관 경로로 간다`() {
        for (f in listOf(file("a.png", "image/png", pngBytes), file("b.jpg", "image/jpeg", jpegBytes))) {
            val id = upload(f)
            mockMvc.perform(get("/api/ingestion/$id"))
                .andExpect(jsonPath("$.data.uploads[0].type").value("FILE"))
                .andExpect(jsonPath("$.data.uploads[0].status").value("PENDING_EXTRACTION"))
        }
    }

    @Test
    fun `같은 엔드포인트에 표 파일을 올리면 파싱 경로로 간다`() {
        val id = upload(file("golden.csv", "text/csv", csv))

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].type").value("BATCH_FILE"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PARSED"))
            .andExpect(jsonPath("$.data.uploads[0].recordCount").value(1))
            .andExpect(jsonPath("$.data.records[0].content['문서ID']").value("DOC-001"))
    }

    @Test
    fun `한 세션에 표 파일과 원본 문서를 섞어 올릴 수 있다`() {
        val id = upload(file("golden.csv", "text/csv", csv))
        mockMvc.perform(multipart("/api/ingestion/$id/uploads").file(pdf())).andExpect(status().isCreated)

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads.length()").value(2))
            .andExpect(jsonPath("$.data.uploads[0].type").value("BATCH_FILE"))
            .andExpect(jsonPath("$.data.uploads[1].type").value("FILE"))
            .andExpect(jsonPath("$.data.records.length()").value(1)) // 문서는 행을 안 만든다
    }

    @Test
    fun `확장자는 보지 않는다 - 확장자 없는 CSV도 파싱된다`() {
        val id = upload(file("noext", "application/octet-stream", csv))

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].type").value("BATCH_FILE"))
            .andExpect(jsonPath("$.data.uploads[0].recordCount").value(1))
    }

    @Test
    fun `확장자는 보지 않는다 - csv 확장자를 단 PDF는 보관 경로로 간다`() {
        val id = upload(file("가짜.csv", "text/csv", pdfBytes))

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads[0].type").value("FILE"))
            .andExpect(jsonPath("$.data.uploads[0].status").value("PENDING_EXTRACTION"))
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
    fun `zip 이지만 xlsx 가 아니면 400 - 스택트레이스 대신 안내`() {
        val docxLike = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(64) { 0x41 }

        mockMvc.perform(multipart("/api/ingestion/uploads").file(file("문서.docx", "application/msword", docxLike)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `텍스트지만 헤더가 틀리면 헤더 누락으로 안내한다`() {
        val wrongHeader = "이름,수량\n연필,3".toByteArray()

        mockMvc.perform(multipart("/api/ingestion/uploads").file(file("other.csv", "text/csv", wrongHeader)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.message").value(containsString("필수 헤더 누락")))
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
            .andExpect(jsonPath("$.data.records.length()").value(1)) // 수기 행은 그대로
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
    fun `원본 문서만 있는 세션은 구조화할 행이 없어 400`() {
        val id = upload(pdf())

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
