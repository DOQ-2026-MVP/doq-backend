package com.doq.comfozi.ingestion.extraction

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 규칙 기반 표 파서 — 제공된 실제 공문으로 검증한다. 뽑은 값은 관찰값이며 틀릴 수 있고,
 * 그래서 검수 단계가 받는다. 여기서 고정하는 건 "이 서식들은 읽어낸다"는 것이다.
 */
class TableTextItemExtractorTest {

    private fun extractFrom(fixture: String): List<ExtractedItem> {
        val bytes = javaClass.getResourceAsStream("/fixtures/$fixture")!!.readBytes()
        return TableTextItemExtractor.extract(fixture, PdfTextExtractor().extract(bytes))
    }

    @Test
    fun `적용일자 열이 있는 서식 - 3개 항목을 열까지 갈라 읽는다`() {
        val items = extractFrom("notice-gaonfood.pdf")

        assertEquals(3, items.size)
        assertEquals(
            ExtractedItem(
                supplier = "가온푸드",
                rawItemName = "토마토살사S/O",
                spec = "4kg/PK",
                unit = "PK",
                priceBefore = "32,000",
                priceAfter = "33,600",
                effectiveDate = "2026-08-01",
            ),
            items[0],
        )
        // 정답 확인 지점 — 조정 대상에서 빠져 단가가 같다(동결)
        assertEquals("72,000" to "72,000", items[1].priceBefore to items[1].priceAfter)
        // 숫자를 품기만 한 품목명은 규격으로 갈라지지 않는다
        assertEquals("나초칩454G" to "454g×12PK/BOX", items[2].rawItemName to items[2].spec)
    }

    @Test
    fun `적용일자 열이 없는 서식 - 품목명에 공백이 있어도 규격과 갈린다`() {
        val items = extractFrom("notice-partial-price.pdf")

        assertEquals(4, items.size)
        assertEquals("푸른들식자재유통", items[0].supplier)
        assertEquals("볶음참깨" to "1 kg", items[0].rawItemName to items[0].spec)
        assertEquals("냉동 감자튀김" to "2 kg", items[1].rawItemName to items[1].spec)
        assertEquals("우동면" to "230 g × 5입", items[3].rawItemName to items[3].spec)
        // 표에 적용일자 열이 없으므로 머리말의 시행일을 쓴다 (원문 그대로 — 정규화는 후속 단계)
        assertContains(items[0].effectiveDate!!, "2026년")
    }

    @Test
    fun `추후 안내 는 공란이 아니라 값으로 담긴다`() {
        val items = extractFrom("notice-partial-price.pdf")

        assertEquals("13,500원", items[2].priceBefore)
        assertEquals("추후 안내", items[2].priceAfter) // 필수값 누락이 아니라 정수 해석 실패 경로
    }

    @Test
    fun `표를 못 읽으면 원문을 한 행으로 내려보낸다`() {
        val items = TableTextItemExtractor.extract("메모.pdf", "표가 없는 안내문입니다.\n감사합니다.")

        assertEquals(1, items.size)
        assertContains(items.single().rawItemName!!, "표가 없는 안내문")
        assertNull(items.single().unit)
    }
}
