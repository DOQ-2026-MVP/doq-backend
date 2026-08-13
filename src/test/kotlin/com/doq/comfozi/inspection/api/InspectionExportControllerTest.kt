package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.inspection.service.InspectionReviewService
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.awaitInspection
import com.doq.comfozi.ingestion.manualInput
import com.doq.comfozi.ingestion.service.IngestionService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

private const val CSV_HEADER =
    "doc_id,source_type,supplier_name,raw_item_name,normalized_item_name,spec,unit," +
        "price_before,price_after,effective_date,review_status,exception_flags," +
        "source_input_method,source_file_name,source_row_no,reviewed_at,review_memo"

@SpringBootTest
@AutoConfigureMockMvc
class InspectionExportControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val reviewService: InspectionReviewService,
    @Autowired val inspectionRepository: InspectionRepository,
    @Autowired val recordRepository: InspectionRecordRepository,
) {

    private fun structured(): Long {
        val session = ingestionService.ingestManual(
            listOf(manualInput(docId = "DOC-1"), manualInput(docId = "DOC-2")),
        )
        structuringService.struct(session.id!!)
        return inspectionRepository.awaitInspection(session.id!!).id!!
    }

    private fun confirmFirst(inspectionId: Long) {
        val first = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId).first()
        reviewService.confirm(first.id!!, null)
    }

    @Test
    fun `export json - 권장 스키마 필드 (편집+메모+이력 포함)`() {
        val inspectionId = structured()
        val record = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId).first()
        reviewService.edit(record.id!!, record.current.copy(supplier = "교정공급사"))
        reviewService.confirm(record.id!!, "검토 완료")

        mockMvc.perform(get("/api/inspection/$inspectionId/export.json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            // 영문 snake_case field ID
            .andExpect(jsonPath("$[0].doc_id").value("DOC-1"))
            .andExpect(jsonPath("$[0].supplier_name").value("교정공급사"))
            .andExpect(jsonPath("$[0].price_before").value(1000)) // 정수
            .andExpect(jsonPath("$[0].review_status").value("approved"))
            .andExpect(jsonPath("$[0].exception_flags").isArray)
            .andExpect(jsonPath("$[0].source_ref.input_method").value("manual")) // 수기 입력
            .andExpect(jsonPath("$[0].review_memo").value("검토 완료"))
            .andExpect(jsonPath("$[0].reviewed_at").value(notNullValue()))
            // change_log: 편집(supplier→supplier_name) 후 확정
            .andExpect(jsonPath("$[0].change_log.length()").value(2))
            .andExpect(jsonPath("$[0].change_log[0].action").value("edit"))
            .andExpect(jsonPath("$[0].change_log[0].field").value("supplier_name"))
            .andExpect(jsonPath("$[0].change_log[0].to").value("교정공급사"))
            .andExpect(jsonPath("$[0].change_log[1].action").value("confirm"))
            .andExpect(jsonPath("$[0].change_log[1].to").value("approved"))
    }

    @Test
    fun `export json - 승인 레코드만`() {
        val inspectionId = structured()
        confirmFirst(inspectionId)

        mockMvc.perform(get("/api/inspection/$inspectionId/export.json"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inspection-$inspectionId.json")))
            .andExpect(jsonPath("$.length()").value(1)) // DOC-2는 NEW라 제외
            .andExpect(jsonPath("$[0].doc_id").value("DOC-1"))
    }

    @Test
    fun `export csv - 평탄화 헤더 + 승인 행`() {
        val inspectionId = structured()
        confirmFirst(inspectionId)

        mockMvc.perform(get("/api/inspection/$inspectionId/export.csv"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inspection-$inspectionId.csv")))
            .andExpect(content().string(containsString(CSV_HEADER)))
            .andExpect(content().string(containsString("DOC-1")))
            .andExpect(content().string(containsString("approved")))
            .andExpect(content().string(not(containsString("DOC-2")))) // 미승인 제외
    }

    @Test
    fun `export json - 승인 0건이면 빈 배열 200`() {
        val inspectionId = structured()

        mockMvc.perform(get("/api/inspection/$inspectionId/export.json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `export csv - 승인 0건이면 헤더만 200`() {
        val inspectionId = structured()

        mockMvc.perform(get("/api/inspection/$inspectionId/export.csv"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(CSV_HEADER)))
            .andExpect(content().string(not(containsString("DOC-1"))))
    }

    @Test
    fun `export - 없는 검수면 404`() {
        mockMvc.perform(get("/api/inspection/999999/export.json"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/inspection/999999/export.csv"))
            .andExpect(status().isNotFound)
    }
}
