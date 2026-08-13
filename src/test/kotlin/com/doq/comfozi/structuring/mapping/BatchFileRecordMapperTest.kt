package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.ingestion.domain.IngestionContent
import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.ingestion.domain.IngestionUploadRef
import com.doq.comfozi.ingestion.domain.IngestionUploadType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatchFileRecordMapperTest {

    private val mapper = BatchFileRecordMapper()

    @Test
    fun `BATCH_FILE 출처를 담당한다`() {
        assertTrue(mapper.supports(IngestionUploadType.BATCH_FILE))
        assertTrue(!mapper.supports(null))
        assertTrue(!mapper.supports(IngestionUploadType.FILE))
    }

    @Test
    fun `정해진 헤더를 캐노니컬 MappedRecord로 매핑한다 (표기 흔들림 흡수, 미정의 컬럼 무시)`() {
        val record = IngestionRecord(
            ingestionId = 1L,
            uploadRef = IngestionUploadRef(uploadId = 1L, uploadType = IngestionUploadType.BATCH_FILE, rowNo = 2),
            content = IngestionContent(
                linkedMapOf(
                    "문서ID" to "DOC-001",
                    "원문 품목명" to "토마토살사S/O", // 공백 표기
                    "기존단가(원)" to "32000", // "(원)" 표기
                    "적용일" to "",
                    "잡컬럼" to "무시",
                ),
            ),
        )

        val observed = mapper.map(record)

        assertEquals("DOC-001", observed.docId)
        assertEquals("토마토살사S/O", observed.rawItemName)
        assertEquals("32000", observed.priceBefore)
        assertEquals("", observed.effectiveDate)
        assertNull(observed.unit) // 파일에 없는 필드
        assertNull(observed.normalizedItemName) // 정규화는 이후 단계
    }
}
