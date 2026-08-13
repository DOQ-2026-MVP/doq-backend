package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.ingestion.domain.IngestionContent
import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.ingestion.domain.IngestionUploadType
import com.doq.common.config.AppObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualRecordMapperTest {

    private val mapper = ManualRecordMapper(AppObjectMapper.instance)

    @Test
    fun `수기 출처(uploadRef 없음)를 담당한다`() {
        assertTrue(mapper.supports(null))
        assertTrue(!mapper.supports(IngestionUploadType.BATCH_FILE))
    }

    @Test
    fun `캐노니컬 키를 그대로 MappedRecord로 읽는다`() {
        val record = IngestionRecord(
            ingestionId = 1L,
            uploadRef = null,
            content = IngestionContent(linkedMapOf("docId" to "MAN-1", "rawItemName" to "임시", "unit" to "PK")),
        )

        val observed = mapper.map(record)

        assertEquals("MAN-1", observed.docId)
        assertEquals("임시", observed.rawItemName)
        assertEquals("PK", observed.unit)
        assertNull(observed.spec)
        assertNull(observed.normalizedItemName)
    }
}
