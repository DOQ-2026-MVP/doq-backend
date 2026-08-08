package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.inspection.service.InspectionReviewService
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
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
    "docId,sourceType,supplier,rawItemName,normalizedItemName,spec,unit,priceBefore,priceAfter,effectiveDate"

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

    /** DOC-1, DOC-2 두 레코드 검수를 만들고 inspectionId를 돌려준다. */
    private fun structured(): Long {
        val session = ingestionService.createFromManualRecords(
            listOf(manualInput(docId = "DOC-1"), manualInput(docId = "DOC-2")),
        )
        structuringService.struct(session.id!!)
        return inspectionRepository.findByIngestionId(session.id!!)!!.id!!
    }

    /** 첫 레코드(DOC-1)만 확정. */
    private fun confirmFirst(inspectionId: Long) {
        val first = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId).first()
        reviewService.confirm(first.id!!, null)
    }

    @Test
    fun `export json - 승인 레코드만 배열로 다운로드`() {
        val inspectionId = structured()
        confirmFirst(inspectionId)

        mockMvc.perform(get("/api/inspection/$inspectionId/export.json"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inspection-$inspectionId.json")))
            .andExpect(jsonPath("$.length()").value(1)) // DOC-2는 NEW라 제외
            .andExpect(jsonPath("$[0].docId").value("DOC-1"))
            .andExpect(jsonPath("$[0].supplier").value("직접입력"))
    }

    @Test
    fun `export csv - 헤더 + 승인 레코드 행`() {
        val inspectionId = structured()
        confirmFirst(inspectionId)

        mockMvc.perform(get("/api/inspection/$inspectionId/export.csv"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inspection-$inspectionId.csv")))
            .andExpect(content().string(containsString(CSV_HEADER)))
            .andExpect(content().string(containsString("DOC-1")))
            .andExpect(content().string(not(containsString("DOC-2")))) // 미승인 제외
    }

    @Test
    fun `export json - 승인 0건이면 빈 배열 200`() {
        val inspectionId = structured() // 아무것도 확정 안 함

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
