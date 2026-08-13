package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.inspection.service.InspectionReviewService
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.ingestion.awaitParsed
import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.ingestion.service.IngestionFileInput
import com.doq.comfozi.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

/**
 * 파일 업로드 경로 end-to-end — 실제 골든 CSV(증빙 20건)를 업로드 → 구조화 → 승인 → export.
 * source_ref(input_method=file · file_name · row_no)와 정규화·가격 정수화가 실제 파일 흐름에서 나오는지 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InspectionExportFromFileTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val reviewService: InspectionReviewService,
    @Autowired val inspectionRepository: InspectionRepository,
    @Autowired val recordRepository: InspectionRecordRepository,
    @Autowired val uploadRepository: IngestionUploadRepository,
) {

    private val goldenCsv: ByteArray =
        javaClass.getResourceAsStream("/fixtures/golden-20.csv")!!.readBytes()

    @Test
    fun `골든 CSV 업로드 후 승인 항목 export에 파일 근거가 실린다`() {
        // 업로드 → 구조화
        val session = ingestionService.ingestFile(
            IngestionFileInput(
                fileName = "golden-20.csv",
                contentType = "text/csv",
                content = goldenCsv.inputStream(),
            ),
        )
        uploadRepository.awaitParsed(session.id!!) // 파싱은 커밋 이후 비동기
        structuringService.struct(session.id!!)

        // DOC-001만 승인
        val inspectionId = inspectionRepository.findByIngestionId(session.id!!)!!.id!!
        val doc001 = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
            .first { it.current.docId == "DOC-001" }
        reviewService.confirm(doc001.id!!, null)

        mockMvc.perform(get("/api/inspection/$inspectionId/export.json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1)) // 승인한 1건만
            .andExpect(jsonPath("$[0].doc_id").value("DOC-001"))
            .andExpect(jsonPath("$[0].source_type").value("PDF"))
            .andExpect(jsonPath("$[0].supplier_name").value("가온푸드(예시)"))
            .andExpect(jsonPath("$[0].normalized_item_name").value("토마토 살사 소스")) // 사전 정규화
            .andExpect(jsonPath("$[0].price_before").value(32000)) // 문자열→정수
            .andExpect(jsonPath("$[0].price_after").value(33600))
            .andExpect(jsonPath("$[0].effective_date").value("2026-08-01"))
            .andExpect(jsonPath("$[0].review_status").value("approved"))
            // 원본 근거 — 파일 출처
            .andExpect(jsonPath("$[0].source_ref.input_method").value("file"))
            .andExpect(jsonPath("$[0].source_ref.file_name").value("golden-20.csv"))
            .andExpect(jsonPath("$[0].source_ref.row_no").value(2)) // 헤더=1행, DOC-001=2행
    }
}
