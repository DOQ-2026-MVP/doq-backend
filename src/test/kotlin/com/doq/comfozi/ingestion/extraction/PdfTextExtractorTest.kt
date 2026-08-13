package com.doq.comfozi.ingestion.extraction

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class PdfTextExtractorTest {

    private val extractor = PdfTextExtractor()

    @Test
    fun `텍스트 레이어가 있는 PDF 는 글자가 그대로 나온다`() {
        val pdf = TestPdf.of("PRICE CHANGE NOTICE", "Tomato Salsa 4kg/PK 32000 -> 33600")

        val text = extractor.extract(pdf)

        assertContains(text, "PRICE CHANGE NOTICE")
        assertContains(text, "32000")
    }

    @Test
    fun `텍스트가 없는 PDF 는 거부한다 - 빈 텍스트를 추출기에 넘기지 않는다`() {
        val e = assertFailsWith<IllegalArgumentException> { extractor.extract(TestPdf.blank()) }

        assertContains(e.message!!, "텍스트를 읽을 수 없는 PDF")
    }

    @Test
    fun `PDF 가 아닌 바이트는 열지 못했다고 알린다`() {
        val e = assertFailsWith<IllegalArgumentException> { extractor.extract("%PDF-1.7 깨진 파일".toByteArray()) }

        assertContains(e.message!!, "PDF 를 열 수 없습니다")
    }
}
