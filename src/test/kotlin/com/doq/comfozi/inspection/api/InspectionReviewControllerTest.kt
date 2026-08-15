package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag.DUPLICATE_SUSPECTED
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag.MISSING_REQUIRED
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag.SPEC_MISMATCH
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag.UNIT_MISMATCH
import com.doq.comfozi.structuring.mapping.MappedRecord
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
class InspectionReviewControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val inspectionRepository: InspectionRepository,
    @Autowired val recordRepository: InspectionRecordRepository,
    @Autowired val objectMapper: ObjectMapper,
) {

    /** 세션을 구조화해 검수를 만들고 inspectionId를 돌려준다. DOC-1·DOC-2는 중복키가 같아 DOC-2가 중복 의심. */
    private fun structured(): Long {
        val session = ingestionService.ingestManual(
            listOf(manualInput(docId = "DOC-1"), manualInput(docId = "DOC-2")),
        )
        structuringService.struct(session.id!!)
        return inspectionRepository.awaitInspection(session.id!!).id!!
    }

    /** DOC-1·DOC-2가 공급사만 달라 서로 중복이 아닌 세션. */
    private fun distinctSession(): Long {
        val session = ingestionService.ingestManual(
            listOf(manualInput(docId = "DOC-1", supplier = "가온"), manualInput(docId = "DOC-2", supplier = "새봄")),
        )
        structuringService.struct(session.id!!)
        return inspectionRepository.awaitInspection(session.id!!).id!!
    }

    private fun firstInspectionRecordId(inspectionId: Long): Long =
        recordRepository.findByInspectionIdOrderByIdAsc(inspectionId).first().id!!

    /** 편집본([values]) 전체를 PATCH — MappedRecord 필드명이 편집 요청 스키마와 일치해 그대로 직렬화한다. */
    private fun patchEdit(inspectionRecordId: Long, values: MappedRecord) =
        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(values)),
        ).andExpect(status().isOk)

    @Test
    fun `PATCH record - current 교체`() {
        val inspectionRecordId = firstInspectionRecordId(structured())

        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"supplier":"교정공급사","normalizedItemName":"교정품목"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.current.supplier").value("교정공급사"))
            .andExpect(jsonPath("$.data.current.normalizedItemName").value("교정품목"))
            .andExpect(jsonPath("$.data.observed.docId").value("DOC-1")) // 관찰값은 불변

        assertEquals("교정공급사", recordRepository.findById(inspectionRecordId).get().current.supplier)
    }

    @Test
    fun `PATCH record - 편집으로 이상 해소 시 per-record 플래그 제거`() {
        // 규격+단위 불일치 레코드
        val session = ingestionService.ingestManual(
            listOf(manualInput(docId = "DOC-X", spec = "기존 1kg / 변경 4단", unit = "KG/단")),
        )
        structuringService.struct(session.id!!)
        val inspectionId = inspectionRepository.awaitInspection(session.id!!).id!!
        val record = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId).first()
        assertEquals(setOf(SPEC_MISMATCH, UNIT_MISMATCH), record.flags) // 초기: 둘 다

        // 단위만 표준값으로 교정(규격은 그대로) → 단위 불일치만 해소
        mockMvc.perform(
            patch("/api/inspection/records/${record.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"docId":"DOC-X","sourceType":"수기","supplier":"직접입력","rawItemName":"임시품목",
                       "spec":"기존 1kg / 변경 4단","unit":"EA","priceBefore":"1000","priceAfter":"1100",
                       "effectiveDate":"2026-08-05","normalizedItemName":"임시품목"}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.flags.length()").value(1))
            .andExpect(jsonPath("$.data.flags[0]").value("SPEC_MISMATCH"))

        assertEquals(setOf(SPEC_MISMATCH), recordRepository.findById(record.id!!).get().flags)
    }

    @Test
    fun `PATCH record - 필수값 비우면 missing_required 추가, 다시 채우면 제거`() {
        val inspectionRecordId = firstInspectionRecordId(structured())

        // 전체 교체로 대부분 필드를 비운다 → 필수값 누락
        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawItemName":"품목만"}"""),
        ).andExpect(status().isOk)
        val cleared = recordRepository.findById(inspectionRecordId).get()
        assertEquals(setOf(MISSING_REQUIRED), cleared.flags)
        // 비운 단가·적용일은 null 로 담긴다 — "null" 이라는 문자열로 담기면 누락으로 보이지 않는다
        assertNull(cleared.current.priceBefore)
        assertNull(cleared.current.effectiveDate)

        // 온전한 값으로 다시 채움 → 플래그 사라짐
        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"docId":"DOC-1","sourceType":"수기","supplier":"직접입력","rawItemName":"임시품목",
                       "spec":"1kg/PK","unit":"PK","priceBefore":"1000","priceAfter":"1100",
                       "effectiveDate":"2026-08-05"}""",
                ),
        ).andExpect(status().isOk)
        assertEquals(emptySet(), recordRepository.findById(inspectionRecordId).get().flags)
    }

    @Test
    fun `PATCH record - 중복키를 깨는 편집 시 중복 의심 해소`() {
        val inspectionId = structured() // DOC-2 = 중복 의심
        val dup = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
            .first { DUPLICATE_SUSPECTED in it.flags }

        // 공급사를 바꿔 중복키를 깬다 → 더 이상 중복 아님
        patchEdit(dup.id!!, dup.current.copy(supplier = "다른공급사"))

        assertEquals(emptySet(), recordRepository.findById(dup.id!!).get().flags)
    }

    @Test
    fun `PATCH record - 편집으로 다른 레코드와 키가 같아지면 중복 의심 추가`() {
        val inspectionId = distinctSession()
        val records = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        val doc1 = records.first { it.current.docId == "DOC-1" }
        val doc2 = records.first { it.current.docId == "DOC-2" }
        assertEquals(emptySet(), doc2.flags) // 처음엔 중복 아님

        // DOC-2 공급사를 DOC-1과 같게 → 중복키 일치 (docId 큰 DOC-2가 중복 의심)
        patchEdit(doc2.id!!, doc2.current.copy(supplier = "가온"))

        assertEquals(setOf(DUPLICATE_SUSPECTED), recordRepository.findById(doc2.id!!).get().flags)
        assertEquals(emptySet(), recordRepository.findById(doc1.id!!).get().flags) // 기준 레코드는 무플래그
    }

    @Test
    fun `PATCH record - 중복키 유지한 채 필수값 비우면 per-record와 중복이 공존`() {
        val inspectionId = structured()
        val dup = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
            .first { DUPLICATE_SUSPECTED in it.flags }

        // rawItemName(비중복키 필수값)만 비운다 — 중복키(normalizedItemName 등)는 그대로
        patchEdit(dup.id!!, dup.current.copy(rawItemName = null))

        assertEquals(setOf(MISSING_REQUIRED, DUPLICATE_SUSPECTED), recordRepository.findById(dup.id!!).get().flags)
    }

    @Test
    fun `PATCH record - 확정된 형제는 매칭에 포함되지만 플래그는 바뀌지 않는다`() {
        val inspectionId = distinctSession()
        val records = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        val doc1 = records.first { it.current.docId == "DOC-1" }
        val doc2 = records.first { it.current.docId == "DOC-2" }

        mockMvc.perform(post("/api/inspection/records/${doc1.id}/confirm")).andExpect(status().isOk)

        // DOC-2를 확정된 DOC-1과 키 일치 → DOC-2엔 중복 의심, DOC-1(확정)은 무변화
        patchEdit(doc2.id!!, doc2.current.copy(supplier = "가온"))

        assertEquals(setOf(DUPLICATE_SUSPECTED), recordRepository.findById(doc2.id!!).get().flags)
        val doc1After = recordRepository.findById(doc1.id!!).get()
        assertEquals(InspectionRecordStatus.CONFIRMED, doc1After.status)
        assertEquals(emptySet(), doc1After.flags) // 확정 레코드 플래그 변경 금지
    }

    @Test
    fun `POST record confirm - NEW to CONFIRMED`() {
        val inspectionRecordId = firstInspectionRecordId(structured())

        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))

        assertEquals(InspectionRecordStatus.CONFIRMED, recordRepository.findById(inspectionRecordId).get().status)
    }

    @Test
    fun `POST record reject - to REJECTED`() {
        val inspectionRecordId = firstInspectionRecordId(structured())

        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/reject"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
    }

    @Test
    fun `PATCH record - 확정된 레코드 편집은 409`() {
        val inspectionRecordId = firstInspectionRecordId(structured())
        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/confirm")).andExpect(status().isOk)

        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"supplier":"뒤늦은교정"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }

    @Test
    fun `POST inspection confirm - 남은 NEW 일괄 확정`() {
        val inspectionId = structured()
        // 한 건 미리 반려 → 남은 NEW 1건만 확정 대상
        val records = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        mockMvc.perform(post("/api/inspection/records/${records[0].id}/reject")).andExpect(status().isOk)

        mockMvc.perform(post("/api/inspection/$inspectionId/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.inspectionId").value(inspectionId))
            .andExpect(jsonPath("$.data.confirmedCount").value(1))
            .andExpect(jsonPath("$.data.blockedCount").value(0))

        val after = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        assertEquals(InspectionRecordStatus.REJECTED, after[0].status)
        assertEquals(InspectionRecordStatus.CONFIRMED, after[1].status)
    }

    @Test
    fun `POST record confirm - 필수값 누락이면 409 (승인 차단)`() {
        val inspectionRecordId = firstInspectionRecordId(structured())
        // 편집으로 필수값을 비운다(전체 교체 — 대부분 필드 null)
        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawItemName":"품목만"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/confirm"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))

        assertEquals(InspectionRecordStatus.NEW, recordRepository.findById(inspectionRecordId).get().status)
    }

    @Test
    fun `POST inspection confirm - 필수값 누락 레코드는 건너뛴다`() {
        val inspectionId = structured()
        val records = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        // records[1] 필수값 누락으로 만들기 → 일괄 확정 시 차단(건너뜀)
        mockMvc.perform(
            patch("/api/inspection/records/${records[1].id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawItemName":"품목만"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(post("/api/inspection/$inspectionId/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.confirmedCount").value(1))
            .andExpect(jsonPath("$.data.blockedCount").value(1))

        val after = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
        assertEquals(InspectionRecordStatus.CONFIRMED, after[0].status)
        assertEquals(InspectionRecordStatus.NEW, after[1].status) // 차단되어 그대로
    }

    @Test
    fun `PATCH record - 메모는 편집으로 남고 전이는 건드리지 않는다`() {
        val inspectionRecordId = firstInspectionRecordId(structured())

        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"supplier":"교정공급사","memo":"공급사 확인함"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memo").value("공급사 확인함"))

        assertEquals("공급사 확인함", recordRepository.findById(inspectionRecordId).get().memo)

        // 메모도 전체 교체 대상 — 다음 편집에서 빠지면 비워진다
        patchEdit(inspectionRecordId, recordRepository.findById(inspectionRecordId).get().current)
        assertNull(recordRepository.findById(inspectionRecordId).get().memo)
    }

    @Test
    fun `POST record reset - 편집본과 메모를 되돌리고 NEW 로`() {
        val inspectionRecordId = firstInspectionRecordId(structured())
        val observed = recordRepository.findById(inspectionRecordId).get().observed

        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"supplier":"교정공급사","memo":"손봄"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/reset"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("NEW"))
            .andExpect(jsonPath("$.data.memo").value(nullValue()))
            .andExpect(jsonPath("$.data.current.supplier").value(observed.supplier))

        val after = recordRepository.findById(inspectionRecordId).get()
        assertEquals(InspectionRecordStatus.NEW, after.status)
        assertEquals(observed, after.current)
        assertNull(after.memo)
    }

    @Test
    fun `POST record reset - 확정된 레코드도 되돌아가 다시 편집할 수 있다`() {
        val inspectionRecordId = firstInspectionRecordId(structured())
        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/confirm")).andExpect(status().isOk)

        mockMvc.perform(post("/api/inspection/records/$inspectionRecordId/reset"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("NEW"))

        // 편집 잠금이 풀렸다
        mockMvc.perform(
            patch("/api/inspection/records/$inspectionRecordId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"supplier":"다시교정"}"""),
        ).andExpect(status().isOk)
    }

    @Test
    fun `POST record reset - 플래그도 인계 직후 판정으로 되돌아간다`() {
        val inspectionId = structured() // DOC-2 = 중복 의심
        val dup = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
            .first { DUPLICATE_SUSPECTED in it.flags }
        patchEdit(dup.id!!, dup.current.copy(supplier = "다른공급사")) // 중복키를 깨 플래그 해소
        assertEquals(emptySet(), recordRepository.findById(dup.id!!).get().flags)

        mockMvc.perform(post("/api/inspection/records/${dup.id}/reset")).andExpect(status().isOk)

        assertEquals(setOf(DUPLICATE_SUSPECTED), recordRepository.findById(dup.id!!).get().flags)
    }

    @Test
    fun `POST record reset - 없는 레코드면 404`() {
        mockMvc.perform(post("/api/inspection/records/999999/reset"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `POST record confirm - 없는 레코드면 404`() {
        mockMvc.perform(post("/api/inspection/records/999999/confirm"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `POST inspection confirm - 없는 검수면 404`() {
        mockMvc.perform(post("/api/inspection/999999/confirm"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
