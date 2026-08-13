package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.awaitParsed
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.structuring.ingestion.service.IngestionFileInput
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
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
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionTruncateTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val ingestionRepository: IngestionRepository,
    @Autowired val uploadRepository: IngestionUploadRepository,
    @Autowired val recordRepository: IngestionRecordRepository,
) {

    private val csv = """
        문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원),적용일
        DOC-001,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,32000,33600,2026-08-01
    """.trimIndent()

    /** 파일 업로드 + 수기 입력이 함께 담긴 세션을 만들고 id를 돌려준다. */
    private fun seed(): Long {
        val session = service.ingestFile(
            IngestionFileInput(fileName = "test.csv", contentType = "text/csv", content = csv.byteInputStream()),
        )
        val id = session.id!!

        service.ingestManual(listOf(manualInput(docId = "MAN-1", rawItemName = "수기품목")), id)
        uploadRepository.awaitParsed(id) // 파싱은 커밋 이후 비동기
        return id
    }

    @Test
    fun `DELETE {id} records - 수기·파일·업로드 모두 비우고 DRAFT로 되돌림`() {
        val id = seed()
        // 검증·실패를 거쳐 FAILED 상태에서 되돌림을 확인
        ingestionRepository.save(ingestionRepository.findByIdOrNull(id)!!.apply { markFailed() })
        assertTrue(recordRepository.findByIngestionIdOrderByIdAsc(id).size >= 2) // 파일 1 + 수기 1
        assertTrue(uploadRepository.findByIngestionIdOrderByIdAsc(id).isNotEmpty())

        mockMvc.perform(delete("/api/ingestion/$id/records"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))

        assertTrue(recordRepository.findByIngestionIdOrderByIdAsc(id).isEmpty())
        assertTrue(uploadRepository.findByIngestionIdOrderByIdAsc(id).isEmpty())
    }

    @Test
    fun `완료(STRUCTURED) 세션 비우기면 409`() {
        val id = seed()
        ingestionRepository.save(ingestionRepository.findByIdOrNull(id)!!.apply { markStructured() })

        mockMvc.perform(delete("/api/ingestion/$id/records"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }

    @Test
    fun `없는 세션 비우기면 404`() {
        mockMvc.perform(delete("/api/ingestion/999999/records"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
