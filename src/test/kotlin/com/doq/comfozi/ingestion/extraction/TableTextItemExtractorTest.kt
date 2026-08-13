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
    fun `적용일자 칸이 비어 있어도 행을 버리지 않는다`() {
        // 요구사항 DOC-016 — 적용일자 미정. 버리면 항목이 통째로 사라진다
        val text = """
            공 급 자  푸른포장 / 담당 김수현
            종이보울500 500EA/BOX BOX 52,000 55,000 2026-08-12
            투명리드500 500EA/BOX BOX 39,000 41,000
        """.trimIndent()

        val items = TableTextItemExtractor.extract("거래명세서.png", text)

        assertEquals(2, items.size)
        assertEquals("푸른포장", items[0].supplier) // 발신 대신 공급자 를 쓰는 서식
        assertEquals("2026-08-12", items[0].effectiveDate)
        assertEquals("투명리드500" to "500EA/BOX", items[1].rawItemName to items[1].spec)
        assertNull(items[1].effectiveDate) // 비어 있는 대로 → 필수값 누락으로 드러난다
    }

    @Test
    fun `기존 변경 규격은 품목명과 갈리지 않고 통째로 규격이 된다`() {
        // 요구사항 DOC-019 — 숫자 규칙만 쓰면 "냉동돈전지 기존" 이 품목명이 돼버린다
        val text = "냉동돈전지 기존 10kg / 변경 9kg BOX 86,000 86,000 2026-08-15"

        val item = TableTextItemExtractor.extract("규격변경.png", text).single()

        assertEquals("냉동돈전지", item.rawItemName)
        assertEquals("기존 10kg / 변경 9kg", item.spec) // 이 형태라야 spec_mismatch 가 물린다
        assertEquals("86,000" to "86,000", item.priceBefore to item.priceAfter) // 동결
    }

    @Test
    fun `OCR 잡음은 표 행으로 오인하지 않는다`() {
        // 서버 OCR 이 괘선을 글자로 읽어 뱉은 실제 줄 — 끝 칸이 선택이라 모양만은 표 행과 같다
        val text = """
            종이보울500 500EA/BOX BOX 52,000 55,000 2026-08-12
            ieee 9699 [ek oe} 900 090
        """.trimIndent()

        val items = TableTextItemExtractor.extract("거래명세서.png", text)

        assertEquals(1, items.size) // 단위 모양(`oe}`)·콤마 없는 단가에서 걸러진다
        assertEquals("종이보울500", items.single().rawItemName)
    }

    @Test
    fun `표를 못 읽으면 원문을 한 행으로 내려보낸다`() {
        val items = TableTextItemExtractor.extract("메모.pdf", "표가 없는 안내문입니다.\n감사합니다.")

        assertEquals(1, items.size)
        assertContains(items.single().rawItemName!!, "표가 없는 안내문")
        assertNull(items.single().unit)
    }
}
