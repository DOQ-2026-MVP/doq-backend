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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionDocumentUploadTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val uploadRepository: IngestionUploadRepository,
    @Autowired val recordRepository: IngestionRecordRepository,
) {

    // 보관만 하므로 검증은 매직 바이트까지다 — 뒤 내용은 파싱되지 않는다.
    private val pdfBytes = "%PDF-1.7\n증빙 원본".toByteArray()
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + "png".toByteArray()
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + "jpg".toByteArray()

    private fun file(name: String, type: String, bytes: ByteArray) = MockMultipartFile("file", name, type, bytes)
    private fun pdf() = file("증빙.pdf", "application/pdf", pdfBytes)

    private fun uploadPdf(): Long {
        val body = mockMvc.perform(multipart("/api/ingestion/documents").file(pdf()))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return Regex("\"ingestionId\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    @Test
    fun `POST documents - PDF는 보관되고 행은 만들어지지 않는다`() {
        val id = uploadPdf()

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
    fun `POST documents - PNG·JPEG도 보관된다`() {
        for (f in listOf(file("a.png", "image/png", pngBytes), file("b.jpg", "image/jpeg", jpegBytes))) {
            mockMvc.perform(multipart("/api/ingestion/documents").file(f))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
        }
    }

    @Test
    fun `POST documents - 확장자만 pdf인 파일은 400 (매직 바이트로 판별)`() {
        val fake = file("가짜.pdf", "application/pdf", "문서ID,원본유형\nDOC-001,PDF".toByteArray())

        mockMvc.perform(multipart("/api/ingestion/documents").file(fake))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `POST {id} documents - 기존 세션에 이어붙는다`() {
        val id = service.createFromManualRecords(listOf(manualInput(docId = "MAN-1"))).id!!

        mockMvc.perform(multipart("/api/ingestion/$id/documents").file(pdf()))
            .andExpect(status().isCreated)

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads.length()").value(1))
            .andExpect(jsonPath("$.data.records.length()").value(1)) // 수기 행은 그대로
    }

    @Test
    fun `GET 업로드 원본 - 올린 바이트를 그대로 돌려준다`() {
        val id = uploadPdf()
        val uploadId = uploadRepository.findByIngestionIdOrderByIdAsc(id).first().id!!

        mockMvc.perform(get("/api/ingestion/$id/uploads/$uploadId/content"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", containsString("inline")))
            .andExpect(content().bytes(pdfBytes))
    }

    @Test
    fun `GET 업로드 원본 - 다른 세션의 업로드면 404`() {
        val mine = uploadPdf()
        val other = uploadPdf()
        val othersUpload = uploadRepository.findByIngestionIdOrderByIdAsc(other).first().id!!

        mockMvc.perform(get("/api/ingestion/$mine/uploads/$othersUpload/content"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `원본 문서만 있는 세션은 구조화할 행이 없어 400`() {
        val id = uploadPdf()

        assertFailsWith<IllegalArgumentException> { structuringService.struct(id) }
    }

    @Test
    fun `원본 문서 업로드도 업로드 단위 삭제가 된다`() {
        val id = uploadPdf()
        val uploadId = uploadRepository.findByIngestionIdOrderByIdAsc(id).first().id!!

        mockMvc.perform(delete("/api/ingestion/$id/uploads/$uploadId"))
            .andExpect(status().isOk)

        assertEquals(0, uploadRepository.findByIngestionIdOrderByIdAsc(id).size)
    }
}
