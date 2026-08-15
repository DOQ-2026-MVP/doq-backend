package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.domain.InspectionChangeType
import com.doq.comfozi.inspection.repository.InspectionChangeLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.awaitInspection
import com.doq.comfozi.ingestion.manualInput
import com.doq.comfozi.ingestion.service.IngestionService
import org.hamcrest.Matchers.nullValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class InspectionChangeLogTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val inspectionRepository: InspectionRepository,
    @Autowired val recordRepository: InspectionRecordRepository,
    @Autowired val changeLogRepository: InspectionChangeLogRepository,
    @Autowired val objectMapper: ObjectMapper,
) {

    private fun structured(): Long {
        val session = ingestionService.ingestManual(
            listOf(manualInput(docId = "DOC-1"), manualInput(docId = "DOC-2")),
        )
        structuringService.struct(session.id!!)
        return inspectionRepository.awaitInspection(session.id!!).id!!
    }

    private fun firstInspectionRecordId(inspectionId: Long): Long =
        recordRepository.findByInspectionIdOrderByIdAsc(inspectionId).first().id!!

    @Test
    fun `편집하면 EDIT 이력이 바뀐 필드 diff만 남는다`() {
        val inspectionRecordId = firstInspectionRecordId(structured())
        // 현재값을 읽어 supplier 한 필드만 바꿔 전체 교체 → diff는 supplier 1건이어야 함
        val current = recordRepository.findById(inspectionRecordId).get().current
        val before = current.supplier
        val body = objectMapper.writeValueAsString(current.copy(supplier = "교정공급사"))

        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/inspection/records/$inspectionRecordId/changelog"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].type").value("EDIT"))
            .andExpect(jsonPath("$.data[0].toStatus").value(nullValue())) // 편집은 상태 전이 없음
            .andExpect(jsonPath("$.data[0].changes.length()").value(1)) // supplier 한 필드만
            .andExpect(jsonPath("$.data[0].changes[0].field").value("supplier"))
            .andExpect(jsonPath("$.data[0].changes[0].before").value(before))
            .andExpect(jsonPath("$.data[0].changes[0].after").value("교정공급사"))
    }

    @Test
    fun `확정하면 메모와 상태 전이가 이력에 남는다`() {
        val inspectionRecordId = firstInspectionRecordId(structured())

        // 메모는 레코드에 남고(응답에 노출), 이력에는 상태 전이만 남는다
        mockMvc.perform(
            post("/api/inspection/records/$inspectionRecordId/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memo":"검토 완료"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.memo").value("검토 완료")) // 메모는 레코드에

        mockMvc.perform(get("/api/inspection/records/$inspectionRecordId/changelog"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].type").value("CONFIRM"))
            .andExpect(jsonPath("$.data[0].fromStatus").value("NEW"))
            .andExpect(jsonPath("$.data[0].toStatus").value("CONFIRMED"))
    }

    @Test
    fun `반려하면 사유는 레코드에, REJECT 이력이 남는다`() {
        val inspectionRecordId = firstInspectionRecordId(structured())

        mockMvc.perform(
            post("/api/inspection/records/$inspectionRecordId/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memo":"규격 재확인 필요"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.memo").value("규격 재확인 필요")) // 메모는 레코드에

        mockMvc.perform(get("/api/inspection/records/$inspectionRecordId/changelog"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].type").value("REJECT"))
            .andExpect(jsonPath("$.data[0].toStatus").value("REJECTED"))
    }

    @Test
    fun `여러 변경이 시각순으로 누적된다`() {
        val inspectionRecordId = firstInspectionRecordId(structured())

        // 전체 교체 편집 — confirm 되려면 필수값이 모두 채워져 있어야 함
        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"docId":"DOC-1","sourceType":"수기","supplier":"교정1","rawItemName":"임시품목",
                     "spec":"1kg/PK","unit":"PK","priceBefore":"1000","priceAfter":"1100","effectiveDate":"2026-08-05"}
                    """.trimIndent(),
                ),
        ).andExpect(status().isOk)
        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/reject")).andExpect(status().isOk)
        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/confirm")).andExpect(status().isOk)

        mockMvc.perform(get("/api/inspection/records/$inspectionRecordId/changelog"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].type").value("EDIT"))
            .andExpect(jsonPath("$.data[1].type").value("REJECT"))
            .andExpect(jsonPath("$.data[2].type").value("CONFIRM"))
            .andExpect(jsonPath("$.data[2].fromStatus").value("REJECTED")) // 반려 → 확정
    }

    @Test
    fun `본문 없이 확정하면 메모는 null`() {
        val inspectionRecordId = firstInspectionRecordId(structured())

        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memo").value(nullValue()))
    }

    @Test
    fun `일괄 확정도 레코드별 CONFIRM 이력을 남긴다`() {
        val inspectionId = structured()

        mockMvc.perform(post("/api/inspection/$inspectionId/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.confirmedCount").value(2))

        recordRepository.findByInspectionIdOrderByIdAsc(inspectionId).forEach { record ->
            val logs = changeLogRepository.findByInspectionRecordIdOrderByIdAsc(record.id!!)
            assertEquals(1, logs.size)
            assertEquals(InspectionChangeType.CONFIRM, logs.first().type)
        }
    }

    @Test
    fun `GET changelog - 없는 레코드면 404`() {
        mockMvc.perform(get("/api/inspection/records/999999/changelog"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
