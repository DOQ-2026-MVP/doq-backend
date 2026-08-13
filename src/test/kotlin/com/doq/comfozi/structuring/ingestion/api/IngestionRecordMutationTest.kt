package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.service.IngestionFileInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import com.doq.common.config.AppObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionRecordMutationTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val ingestionRepository: IngestionRepository,
    @Autowired val recordRepository: IngestionRecordRepository,
) {

    private val csv = """
        문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원),적용일
        DOC-001,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,32000,33600,2026-08-01
    """.trimIndent()

    /** 파일 1행 + 수기 2행이 담긴 세션. */
    private fun seed(): Long {
        val session = service.ingestFile(
            IngestionFileInput("test.csv", "text/csv", csv.byteInputStream()),
        )
        val id = session.id!!
        service.ingestManual(listOf(manualInput(docId = "MAN-1"), manualInput(docId = "MAN-2")), id)
        return id
    }

    private fun records(ingestionId: Long): List<IngestionRecord> =
        recordRepository.findByIngestionIdOrderByIdAsc(ingestionId)

    private fun fileRecordId(ingestionId: Long) = records(ingestionId).first { it.uploadRef != null }.id!!
    private fun manualRecordId(ingestionId: Long) = records(ingestionId).first { it.uploadRef == null }.id!!

    private fun body(docId: String = "MAN-1", rawItemName: String = "고친품목", priceAfter: Long = 2200) =
        AppObjectMapper.instance.writeValueAsString(
            mapOf(
                "docId" to docId,
                "sourceType" to "수기",
                "supplier" to "직접입력",
                "rawItemName" to rawItemName,
                "spec" to "1kg/PK",
                "unit" to "PK",
                "priceBefore" to 2000,
                "priceAfter" to priceAfter,
                "effectiveDate" to "2026-09-01",
            ),
        )

    @Test
    fun `PUT 수기 행 - 원문을 교체한다`() {
        val id = seed()
        val recordId = manualRecordId(id)

        mockMvc.perform(put("/api/ingestion/$id/records/$recordId").contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(recordId))
            .andExpect(jsonPath("$.data.content.rawItemName").value("고친품목"))
            .andExpect(jsonPath("$.data.content.priceAfter").value("2200"))
            .andExpect(jsonPath("$.data.uploadId").isEmpty) // 수기 행 그대로

        val saved = recordRepository.findByIdOrNull(recordId)!!
        assertEquals("고친품목", saved.content.values["rawItemName"])
        assertEquals("2026-09-01", saved.content.values["effectiveDate"])
    }

    @Test
    fun `PUT 파일 출처 행은 409 - 원본 근거는 인입 단계에서 못 고친다`() {
        val id = seed()
        val recordId = fileRecordId(id)

        mockMvc.perform(put("/api/ingestion/$id/records/$recordId").contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))

        assertEquals("DOC-001", recordRepository.findByIdOrNull(recordId)!!.content.values["문서ID"])
    }

    @Test
    fun `PUT 필수 필드가 빠지면 400`() {
        val id = seed()

        mockMvc.perform(
            put("/api/ingestion/$id/records/${manualRecordId(id)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"docId":"MAN-1"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `DELETE 행 - 그 행만 사라지고 나머지는 남는다`() {
        val id = seed()
        val recordId = manualRecordId(id)

        mockMvc.perform(delete("/api/ingestion/$id/records/$recordId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))

        assertNull(recordRepository.findByIdOrNull(recordId))
        assertEquals(2, records(id).size) // 파일 1 + 수기 1
    }

    @Test
    fun `DELETE 파일 출처 행도 지울 수 있고 업로드는 남는다`() {
        val id = seed()

        mockMvc.perform(delete("/api/ingestion/$id/records/${fileRecordId(id)}"))
            .andExpect(status().isOk)

        assertEquals(0, records(id).count { it.uploadRef != null })
        // 업로드는 그대로 — 행 수만 줄어든다
        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(jsonPath("$.data.uploads.length()").value(1))
            .andExpect(jsonPath("$.data.uploads[0].recordCount").value(0))
    }

    @Test
    fun `완료(STRUCTURED) 세션은 행 삭제·수정 모두 409`() {
        val id = seed()
        val recordId = manualRecordId(id)
        ingestionRepository.save(ingestionRepository.findByIdOrNull(id)!!.apply { markStructured() })

        mockMvc.perform(delete("/api/ingestion/$id/records/$recordId"))
            .andExpect(status().isConflict)
        mockMvc.perform(put("/api/ingestion/$id/records/$recordId").contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isConflict)

        assertEquals(3, records(id).size)
    }

    @Test
    fun `다른 세션의 행 id면 404`() {
        val mine = seed()
        val other = seed()
        val othersRecord = manualRecordId(other)

        mockMvc.perform(delete("/api/ingestion/$mine/records/$othersRecord"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        mockMvc.perform(
            put("/api/ingestion/$mine/records/$othersRecord").contentType(MediaType.APPLICATION_JSON).content(body()),
        )
            .andExpect(status().isNotFound)

        assertEquals(3, records(other).size)
    }
}
