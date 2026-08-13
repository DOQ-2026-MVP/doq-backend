package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.awaitParsed
import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.structuring.ingestion.service.IngestionFileInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import com.doq.comfozi.structuring.ingestion.support.FileStorage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionUploadDeleteTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val ingestionRepository: IngestionRepository,
    @Autowired val uploadRepository: IngestionUploadRepository,
    @Autowired val recordRepository: IngestionRecordRepository,
    @Autowired val fileStorage: FileStorage,
) {

    private fun csv(vararg docIds: String) = buildString {
        appendLine("문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원),적용일")
        docIds.forEach { appendLine("$it,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,32000,33600,2026-08-01") }
    }

    private fun upload(ingestionId: Long?, fileName: String, vararg docIds: String): Long {
        val input = IngestionFileInput(fileName, "text/csv", csv(*docIds).byteInputStream())
        val session = if (ingestionId == null) service.ingestFile(input)
        else service.ingestFile(input, ingestionId)
        return session.id!!.also { uploadRepository.awaitParsed(it) } // 파싱은 커밋 이후 비동기
    }

    /** 파일 2개 + 수기 1건이 담긴 세션. */
    private fun seed(): Long {
        val id = upload(null, "first.csv", "DOC-001", "DOC-002")
        upload(id, "second.csv", "DOC-003")
        service.ingestManual(listOf(manualInput(docId = "MAN-1")), id)
        return id
    }

    private fun uploads(ingestionId: Long): List<IngestionUpload> =
        uploadRepository.findByIngestionIdOrderByIdAsc(ingestionId)

    private fun docIdsOf(ingestionId: Long): List<String?> =
        recordRepository.findByIngestionIdOrderByIdAsc(ingestionId)
            .map { it.content.values["문서ID"] ?: it.content.values["docId"] }

    @Test
    fun `DELETE 업로드 - 해당 업로드의 행만 지우고 다른 업로드·수기 행은 남긴다`() {
        val id = seed()
        val first = uploads(id).first()

        mockMvc.perform(delete("/api/ingestion/$id/uploads/${first.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.ingestionId").value(id))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            // 응답이 곧 지운 뒤 현황이다 — 다시 조회할 필요가 없다
            .andExpect(jsonPath("$.data.uploads.length()").value(1))
            .andExpect(jsonPath("$.data.uploads[0].fileName").value("second.csv"))
            .andExpect(jsonPath("$.data.manualRecords.length()").value(1))

        assertEquals(listOf("second.csv"), uploads(id).map { it.fileName })
        assertEquals(listOf("DOC-003", "MAN-1"), docIdsOf(id))
    }

    @Test
    fun `DELETE 업로드 - 저장 원본도 함께 지운다`() {
        val id = seed()
        val first = uploads(id).first()
        fileStorage.load(first.storageKey).close() // 삭제 전에는 읽힌다

        mockMvc.perform(delete("/api/ingestion/$id/uploads/${first.id}"))
            .andExpect(status().isOk)

        assertNull(uploadRepository.findByIdOrNull(first.id!!))
        assertFailsWith<Exception> { fileStorage.load(first.storageKey) }
    }

    @Test
    fun `DELETE 업로드 - 실패(FAILED) 세션은 DRAFT로 되돌린다`() {
        val id = seed()
        ingestionRepository.save(ingestionRepository.findByIdOrNull(id)!!.apply { markFailed() })

        mockMvc.perform(delete("/api/ingestion/$id/uploads/${uploads(id).first().id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
    }

    @Test
    fun `완료(STRUCTURED) 세션의 업로드 삭제는 409`() {
        val id = seed()
        val uploadId = uploads(id).first().id!!
        ingestionRepository.save(ingestionRepository.findByIdOrNull(id)!!.apply { markStructured() })

        mockMvc.perform(delete("/api/ingestion/$id/uploads/$uploadId"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))

        assertTrue(uploads(id).isNotEmpty()) // 아무것도 지워지지 않았다
    }

    @Test
    fun `다른 세션의 업로드 id면 404이고 그 세션은 그대로다`() {
        val mine = seed()
        val other = upload(null, "other.csv", "DOC-009")
        val othersUpload = uploads(other).first()

        mockMvc.perform(delete("/api/ingestion/$mine/uploads/${othersUpload.id}"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))

        assertEquals(listOf("other.csv"), uploads(other).map { it.fileName })
        assertEquals(listOf("DOC-009"), docIdsOf(other))
    }

    @Test
    fun `없는 업로드 id면 404`() {
        val id = seed()

        mockMvc.perform(delete("/api/ingestion/$id/uploads/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `없는 세션이면 404`() {
        mockMvc.perform(delete("/api/ingestion/999999/uploads/1"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
