package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.domain.InspectionChangeType
import com.doq.comfozi.inspection.repository.InspectionChangeLogRepository
import com.doq.comfozi.structuring.mapping.MappedRecord
import com.fasterxml.jackson.core.type.TypeReference
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

    /** 편집 요청 본문 — 편집본 전체([values])에 메모를 얹는다(편집이 값과 메모를 함께 교체한다). */
    private fun editBody(values: MappedRecord, memo: String? = null): String {
        val body = objectMapper.convertValue(values, object : TypeReference<MutableMap<String, Any?>>() {})
        body["memo"] = memo
        return objectMapper.writeValueAsString(body)
    }

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
    fun `확정하면 상태 전이만 이력에 남고 편집으로 남긴 메모는 유지된다`() {
        val inspectionRecordId = firstInspectionRecordId(structured())
        val current = recordRepository.findById(inspectionRecordId).get().current

        // 메모는 편집으로 남긴다 — 레코드에 붙고(응답에 노출) 이력엔 남지 않는다
        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody(current, "검토 완료")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memo").value("검토 완료"))

        // 확정은 메모를 건드리지 않는다
        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.memo").value("검토 완료"))

        mockMvc.perform(get("/api/inspection/records/$inspectionRecordId/changelog"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[1].type").value("CONFIRM"))
            .andExpect(jsonPath("$.data[1].fromStatus").value("NEW"))
            .andExpect(jsonPath("$.data[1].toStatus").value("CONFIRMED"))
    }

    @Test
    fun `반려하면 REJECT 이력이 남고 메모는 그대로다`() {
        val inspectionRecordId = firstInspectionRecordId(structured())
        val current = recordRepository.findById(inspectionRecordId).get().current
        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody(current, "규격 재확인 필요")),
        ).andExpect(status().isOk)

        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/reject"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.memo").value("규격 재확인 필요")) // 전이는 메모를 건드리지 않는다

        mockMvc.perform(get("/api/inspection/records/$inspectionRecordId/changelog"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[1].type").value("REJECT"))
            .andExpect(jsonPath("$.data[1].toStatus").value("REJECTED"))
    }

    @Test
    fun `초기화하면 되돌린 필드 diff와 NEW 전이가 RESET 이력에 남는다`() {
        val inspectionRecordId = firstInspectionRecordId(structured())
        val observed = recordRepository.findById(inspectionRecordId).get().observed
        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody(observed.copy(supplier = "교정공급사"))),
        ).andExpect(status().isOk)
        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/confirm")).andExpect(status().isOk)

        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/reset")).andExpect(status().isOk)

        mockMvc.perform(get("/api/inspection/records/$inspectionRecordId/changelog"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[2].type").value("RESET"))
            .andExpect(jsonPath("$.data[2].fromStatus").value("CONFIRMED"))
            .andExpect(jsonPath("$.data[2].toStatus").value("NEW"))
            .andExpect(jsonPath("$.data[2].changes.length()").value(1)) // 되돌린 supplier 한 필드
            .andExpect(jsonPath("$.data[2].changes[0].field").value("supplier"))
            .andExpect(jsonPath("$.data[2].changes[0].before").value("교정공급사"))
            .andExpect(jsonPath("$.data[2].changes[0].after").value(observed.supplier))
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
    fun `편집으로 메모를 남긴 적 없으면 확정해도 메모는 null`() {
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
