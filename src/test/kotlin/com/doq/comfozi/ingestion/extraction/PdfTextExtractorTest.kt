package com.doq.comfozi.ingestion.extraction

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class PdfTextExtractorTest {

    private val extractor = PdfTextExtractor()

    /** 제공된 원본 공문 (요구사항 `additional_inputs/` 에서 복사). */
    private fun fixture(name: String): ByteArray =
        javaClass.getResourceAsStream("/fixtures/$name")!!.readBytes()

    /** 줄바꿈·전각 공백까지 한 칸으로 — 자리 맞춤 공백에 단언이 흔들리지 않게. */
    private fun ByteArray.textNormalized(): String =
        extractor.extract(this).replace(Regex("[\\s\\u3000]+"), " ")

    @Test
    fun `텍스트 레이어가 있는 PDF 는 글자가 그대로 나온다`() {
        val pdf = TestPdf.of("PRICE CHANGE NOTICE", "Tomato Salsa 4kg/PK 32000 -> 33600")

        val text = extractor.extract(pdf)

        assertContains(text, "PRICE CHANGE NOTICE")
        assertContains(text, "32000")
    }

    @Test
    fun `제공 공문은 표 한 행이 한 줄로 나온다 - 열 대응이 살아 있다`() {
        val text = fixture("notice-gaonfood.pdf").textNormalized()

        // 이 문서가 OCR 없이 읽힌다는 요구사항의 전제를 여기서 고정한다
        assertContains(text, "토마토살사S/O 4kg/PK PK 32,000 33,600 2026-08-01")
        assertContains(text, "나초칩454G 454g×12PK/BOX BOX 48,000 51,000 2026-08-05")
        // 정답 확인 지점 — 할라피뇨는 조정 대상에서 빠져 단가가 같다(동결)
        assertContains(text, "할라피뇨슬라이스 3kg×6CAN/BOX BOX 72,000 72,000 2026-08-01")
    }

    @Test
    fun `추후 안내 처럼 숫자가 아닌 단가도 텍스트에 그대로 있다`() {
        val text = fixture("notice-partial-price.pdf").textNormalized()

        // 공란이 아니라 값이 있는 경우 — 필수값 누락과 구분돼야 한다
        assertContains(text, "청양고추 1 kg 박스 13,500원 추후 안내")
        // 이 문서는 표에 적용일자 열이 없다. 적용일은 머리말에 있다(프롬프트가 이걸 설명한다)
        assertContains(text, "번호 품목명 규격 단위 기존단가 변경단가 인상률")
        assertContains(text, "시행일 2026년 8월 20일")
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
