package com.doq.comfozi.inspection.api

import com.doq.comfozi.ingestion.awaitParsed
import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.ingestion.service.IngestionFileInput
import com.doq.comfozi.ingestion.service.IngestionService
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.awaitInspection
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 검수 화면은 서버가 준 검수값을 그대로 되돌려 보낸다. 그래서 적재된 금액 표기가 편집 요청의
 * 타입(`Long`)으로 되읽히지 않으면, 검수자가 금액을 건드리지 않고 저장만 눌러도 막힌다.
 *
 * 실제로 취합 표는 "86,000   " 처럼 쉼표·공백이 붙은 셀을 물고 온다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class InspectionEditUntouchedPriceTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val inspectionRepository: InspectionRepository,
    @Autowired val recordRepository: InspectionRecordRepository,
    @Autowired val uploadRepository: IngestionUploadRepository,
) {

    /** 금액 칸에 자릿수 쉼표와 꼬리 공백이 붙은 취합 표. */
    private val csv = """
        문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원),적용일
        DOC-001,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,"86,000   ","91,000",2026-08-01
    """.trimIndent()

    @Test
    fun `쉼표 붙은 금액도 적재 시 숫자로 정리돼, 금액을 건드리지 않은 저장이 통과한다`() {
        val session = ingestionService.ingestFile(
            IngestionFileInput(fileName = "comma-price.csv", contentType = "text/csv", content = csv.byteInputStream()),
        )
        uploadRepository.awaitParsed(session.id!!) // 파싱은 커밋 이후 비동기
        structuringService.struct(session.id!!)

        val inspectionId = inspectionRepository.awaitInspection(session.id!!).id!!
        val record = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId).first()

        // 적재 단계에서 이미 정리돼 있어야 한다 — 여기서 어긋나면 아래 저장이 막힌다.
        assertEquals("86000", record.current.priceBefore)
        assertEquals("91000", record.current.priceAfter)

        // 화면이 하는 그대로: 받은 검수값을 손대지 않고 되돌려 보낸다.
        val echoed = mapOf(
            "docId" to record.current.docId,
            "sourceType" to record.current.sourceType,
            "supplier" to record.current.supplier,
            "rawItemName" to record.current.rawItemName,
            "spec" to record.current.spec,
            "unit" to record.current.unit,
            "priceBefore" to record.current.priceBefore,
            "priceAfter" to record.current.priceAfter,
            "effectiveDate" to record.current.effectiveDate,
            "normalizedItemName" to record.current.normalizedItemName,
        )

        mockMvc.perform(
            patch("/api/inspection/records/${record.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(echoed)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.current.priceBefore").value("86000"))
            .andExpect(jsonPath("$.data.current.priceAfter").value("91000"))
    }
}
