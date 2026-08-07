package com.doq.comfozi.structuring.ingestion

import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.service.IngestionBatchFileInput
import com.doq.comfozi.structuring.ingestion.service.IngestionManualInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionServiceTest(
    @Autowired val service: IngestionService,
    @Autowired val recordRepository: IngestionRecordRepository,
) {

    @Test
    fun `취합 CSV 업로드 시 세션과 원본 행이 적재된다`() {
        val csv = """
            문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원),적용일
            DOC-001,PDF,가온푸드(예시),토마토살사S/O,4kg/PK,PK,32000,33600,2026-08-01
            DOC-016,IMAGE,푸른포장(예시),투명리드500,500EA/BOX,BOX,39000,41000,
        """.trimIndent()

        val ingestion = service.createFromBatchFile(
            IngestionBatchFileInput(fileName = "test.csv", contentType = "text/csv", content = csv.byteInputStream()),
        )

        assertNotNull(ingestion.id)
        assertEquals(IngestionStatus.DRAFT, ingestion.status)

        val records = recordRepository.findByIngestionIdOrderByIdAsc(ingestion.id!!)
        assertEquals(2, records.size)

        val first = records[0]
        assertEquals("DOC-001", first.content.values["문서ID"])
        assertEquals("토마토살사S/O", first.content.values["원문 품목명"])
        assertEquals("32000", first.content.values["기존단가(원)"])
        assertEquals(IngestionUploadType.BATCH_FILE, first.uploadRef?.uploadType)
        assertEquals(2, first.uploadRef?.rowNo)

        // DOC-016: 적용일 공란 → 원문 그대로("") 보관, 매핑/검증은 후속 단계
        assertEquals("", records[1].content.values["적용일"])
        assertEquals("DOC-016", records[1].content.values["문서ID"])
    }

    @Test
    fun `수기 입력으로 새 DRAFT 세션에 uploadRef 없는 행이 적재된다`() {
        val session = service.createFromManualRecords(
            listOf(
                IngestionManualInput(
                    docId = "MAN-1",
                    sourceType = "수기",
                    supplier = "직접입력",
                    rawItemName = "임시품목",
                    spec = "1kg/PK",
                    unit = "PK",
                    priceBefore = 1000,
                    priceAfter = 1100,
                    effectiveDate = LocalDate.of(2026, 8, 5),
                ),
            ),
        )

        assertNotNull(session.id)
        assertEquals(IngestionStatus.DRAFT, session.status)

        val records = recordRepository.findByIngestionIdOrderByIdAsc(session.id!!)
        assertEquals(1, records.size)
        val record = records[0]
        assertNull(record.uploadRef) // 수기 = 업로드 출처 없음
        assertEquals("MAN-1", record.content.values["docId"])
    }

    @Test
    fun `XLSX 업로드도 분류되어 파싱·적재된다`() {
        val header = listOf("문서ID", "원본유형", "공급사", "원문 품목명", "규격", "단위", "기존단가(원)", "변경단가(원)", "적용일")
        val row = listOf("DOC-002", "XLSX", "새봄식품(예시)", "허브염지닭정육", "2kg×6PK/BOX", "BOX", "84000", "88200", "2026-08-01")
        val bytes = ByteArrayOutputStream().use { bos ->
            XSSFWorkbook().use { wb ->
                val sheet = wb.createSheet()
                sheet.createRow(0).let { r -> header.forEachIndexed { i, h -> r.createCell(i).setCellValue(h) } }
                sheet.createRow(1).let { r -> row.forEachIndexed { i, v -> r.createCell(i).setCellValue(v) } }
                wb.write(bos)
            }
            bos.toByteArray()
        }

        val ingestion = service.createFromBatchFile(
            IngestionBatchFileInput(fileName = "golden.xlsx", contentType = null, content = bytes.inputStream()),
        )

        val records = recordRepository.findByIngestionIdOrderByIdAsc(ingestion.id!!)
        assertEquals(1, records.size)
        val rec = records[0]
        assertEquals("DOC-002", rec.content.values["문서ID"])
        assertEquals("허브염지닭정육", rec.content.values["원문 품목명"])
        assertEquals("88200", rec.content.values["변경단가(원)"])
        assertEquals(IngestionUploadType.BATCH_FILE, rec.uploadRef?.uploadType)
    }
}
