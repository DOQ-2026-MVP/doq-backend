package com.doq.comfozi.ingestion.service

import com.doq.comfozi.ingestion.manualInput
import com.doq.comfozi.ingestion.domain.IngestionUploadType
import com.doq.comfozi.ingestion.support.IngestionUploadBatchFileContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IngestionRecordConversionTest {

    @Test
    fun `취합 파일 content를 원본 행 엔티티로 변환한다`() {
        val content = IngestionUploadBatchFileContent(
            header = listOf("문서ID", "원본유형", "공급사", "원문 품목명", "규격", "단위", "기존단가(원)", "변경단가(원)", "적용일"),
            rows = listOf(
                listOf("DOC-001", "PDF", "가온푸드", "토마토살사S/O", "4kg/PK", "PK", "32000", "33600", "2026-08-01"),
                listOf("DOC-016", "IMAGE", "푸른포장", "투명리드500", "500EA/BOX", "BOX", "39000", "41000", ""),
            ),
        )

        val records = content.toEntities(ingestionId = 10L, uploadId = 20L)

        assertEquals(2, records.size)
        val first = records[0]
        assertEquals(10L, first.ingestionId)
        assertEquals("DOC-001", first.content.values["문서ID"])
        assertEquals("토마토살사S/O", first.content.values["원문 품목명"])
        assertEquals("32000", first.content.values["기존단가(원)"])
        assertEquals(20L, first.uploadRef?.uploadId)
        assertEquals(IngestionUploadType.BATCH_FILE, first.uploadRef?.uploadType)
        assertEquals(2, first.uploadRef?.rowNo) // 파일 행번호 (헤더=1행)
        assertEquals(3, records[1].uploadRef?.rowNo)
        assertEquals("", records[1].content.values["적용일"]) // 빈 값 → 원문 그대로("")
    }

    @Test
    fun `수기 입력을 uploadRef 없는 원본 행으로 변환한다`() {
        val record = manualInput(docId = "MAN-1", rawItemName = "임시품목").toEntity(ingestionId = 10L)

        assertEquals(10L, record.ingestionId)
        assertEquals("MAN-1", record.content.values["docId"])
        assertEquals("임시품목", record.content.values["rawItemName"])
        assertNull(record.uploadRef)
    }
}
