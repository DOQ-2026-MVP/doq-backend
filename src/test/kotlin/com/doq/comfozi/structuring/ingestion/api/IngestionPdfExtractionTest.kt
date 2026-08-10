package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.inspection.service.InspectionReviewService
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag.SPEC_MISMATCH
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag.UNIT_MISMATCH
import com.doq.comfozi.structuring.ingestion.pdf.PdfExtractedItem
import com.doq.comfozi.structuring.ingestion.pdf.PdfRecordExtractor
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PDF 인입 end-to-end — 추출기를 페이크로 대체(실제 API 호출 없음)해 업로드 → 구조화 → 검수 → export 흐름을 검증한다.
 * FILE 매핑·이상 탐지가 PDF 경로에서도 동작하고, source_ref(input_method=file·file_name·row_no)가 실리는지 확인.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IngestionPdfExtractionTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val structuringService: StructuringService,
    @Autowired val reviewService: InspectionReviewService,
    @Autowired val inspectionRepository: InspectionRepository,
    @Autowired val recordRepository: InspectionRecordRepository,
) {

    /** 실제 Claude 대신 고정 항목 2건(정상 DOC-001 + 규격·단위 불일치 DOC-020)을 돌려주는 페이크. */
    @TestConfiguration
    class FakeExtractorConfig {
        @Bean
        fun pdfRecordExtractor(): PdfRecordExtractor = PdfRecordExtractor { _, _ ->
            listOf(
                PdfExtractedItem(
                    docId = "DOC-001", sourceType = "PDF", supplier = "가온푸드(예시)", rawItemName = "토마토살사S/O",
                    spec = "4kg/PK", unit = "PK", priceBefore = "32000", priceAfter = "33600", effectiveDate = "2026-08-01",
                ),
                PdfExtractedItem(
                    docId = "DOC-020", sourceType = "수기", supplier = "한결유통(예시)", rawItemName = "고수4단",
                    spec = "기존 1kg / 변경 4단", unit = "KG/단", priceBefore = "28000", priceAfter = "22000",
                    effectiveDate = "2026-08-03",
                ),
            )
        }
    }

    @Test
    fun `PDF 업로드 후 추출 항목이 구조화·탐지되고 export에 파일 근거가 실린다`() {
        val pdf = MockMultipartFile("file", "evidence.pdf", "application/pdf", "%PDF-1.4 dummy".toByteArray())

        val body = mockMvc.perform(multipart("/api/ingestion/pdf").file(pdf))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val ingestionId = objectMapper.readTree(body).path("data").path("ingestionId").asLong()

        structuringService.struct(ingestionId)

        val inspectionId = inspectionRepository.findByIngestionId(ingestionId)!!.id!!
        val records = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)

        // DOC-020: 추출 경로에서도 규격+단위 불일치가 탐지된다
        val doc020 = records.first { it.current.docId == "DOC-020" }
        assertEquals(setOf(SPEC_MISMATCH, UNIT_MISMATCH), doc020.flags)

        // DOC-001(정상)만 승인 → export에 파일 근거가 실린다
        val doc001 = records.first { it.current.docId == "DOC-001" }
        reviewService.confirm(doc001.id!!, null)

        mockMvc.perform(get("/api/inspection/$inspectionId/export.json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].doc_id").value("DOC-001"))
            .andExpect(jsonPath("$[0].source_ref.input_method").value("file"))
            .andExpect(jsonPath("$[0].source_ref.file_name").value("evidence.pdf"))
            .andExpect(jsonPath("$[0].source_ref.row_no").value(1)) // 첫 추출 항목 = 순번 1
    }
}
