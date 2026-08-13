package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.ingestion.manualInput
import com.doq.common.config.AppObjectMapper
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 수기 왕복 회귀 테스트 — `IngestionManualInput.toEntity`(직렬화)와 [ManualRecordMapper.map](역직렬화)이
 * Jackson **프로퍼티명**으로 이어진다. 두 클래스의 프로퍼티명이 어긋나면(한쪽만 리네임) 여기서 깨진다.
 */
class ManualRoundTripTest {

    private val mapper = ManualRecordMapper(AppObjectMapper.instance)

    @Test
    fun `수기 입력이 toEntity→map 왕복 후 전 필드 보존된다`() {
        val input = manualInput(
            docId = "DOC-1",
            sourceType = "PDF",
            supplier = "가온푸드",
            rawItemName = "토마토살사",
            spec = "4kg/PK",
            unit = "PK",
            priceBefore = 32000,
            priceAfter = 33600,
            effectiveDate = LocalDate.of(2026, 8, 1),
        )

        val observed = mapper.map(input.toEntity(ingestionId = 1L))

        assertEquals("DOC-1", observed.docId)
        assertEquals("PDF", observed.sourceType)
        assertEquals("가온푸드", observed.supplier)
        assertEquals("토마토살사", observed.rawItemName)
        assertEquals("4kg/PK", observed.spec)
        assertEquals("PK", observed.unit)
        assertEquals("32000", observed.priceBefore) // Long → 문자열
        assertEquals("33600", observed.priceAfter)
        assertEquals("2026-08-01", observed.effectiveDate) // LocalDate → ISO 문자열
    }
}
