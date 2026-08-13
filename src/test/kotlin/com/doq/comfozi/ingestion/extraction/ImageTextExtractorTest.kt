package com.doq.comfozi.ingestion.extraction

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 이미지 OCR — 제공된 실제 공문 2장으로 검증한다.
 *
 * OCR 실행 파일과 한국어 학습데이터가 있어야 돌아가므로 **없는 환경에서는 건너뛴다**
 * (CI·동료 머신을 깨지 않기 위해서다). 설치 방법은 README 실행 안내에 있다.
 */
class ImageTextExtractorTest {

    private val extractor = ImageTextExtractor(command = "tesseract", languages = "kor+eng", timeoutSeconds = 60)

    private fun image(name: String): ByteArray =
        javaClass.getResourceAsStream("/fixtures/$name")!!.readBytes()

    /** 열 사이 간격은 한 칸으로 — 자리 맞춤 공백에 단언이 흔들리지 않게. */
    private fun rowsOf(name: String): List<String> =
        extractor.extract(image(name)).lines().map { it.replace(Regex("\\s+"), " ").trim() }

    private fun assumeOcrInstalled() {
        assumeTrue(extractor.isAvailable, "tesseract 가 설치돼 있지 않아 건너뜀")
        assumeTrue(
            runCatching { ProcessBuilder("tesseract", "--list-langs").start() }
                .getOrNull()?.inputStream?.bufferedReader()?.readText()?.contains("kor") == true,
            "한국어 학습데이터(kor)가 없어 건너뜀",
        )
    }

    @Test
    fun `거래명세서 사진에서 표 한 행이 한 줄로 나온다`() {
        assumeOcrInstalled()

        val rows = rowsOf("notice-pureun.png")

        assertContains(rows, "종이보울500 500EA/BOX BOX 52,000 55,000 2026-08-12")
        // 적용일자 칸이 비어 있는 행 (요구사항 DOC-016) — 끝 칸 없이 그대로 나온다
        assertContains(rows, "투명리드500 500EA/BOX BOX 39,000 41,000")
    }

    @Test
    fun `규격 변경 통보 사진도 한 행으로 나온다 - 괘선은 걷어낸다`() {
        assumeOcrInstalled()

        val row = rowsOf("notice-saebom.png").single { it.contains("86,000") }

        // 표 세로선이 `|`·홀로 선 `=` 로 섞여 들어오는데, 값은 남기고 그것만 지운다
        assertEquals(false, row.contains('|'))
        assertContains(row, "BOX 86,000 86,000 2026-08-15")
        assertContains(row, "변경 9kg") // 규격 변경 통보 형태가 살아 있다
    }

    @Test
    fun `사진에서 뽑은 텍스트가 규칙 파서까지 통과한다`() {
        assumeOcrInstalled()

        // OCR 출력에는 괘선이 글자로 섞여 들어온다 — 그것이 표 행으로 둔갑하지 않아야 한다
        val items = TableTextItemExtractor.extract("거래명세서.png", extractor.extract(image("notice-pureun.png")))

        assertEquals(2, items.size)
        assertEquals("500EA/BOX" to "BOX", items[0].spec to items[0].unit)
        assertEquals("52,000" to "55,000", items[0].priceBefore to items[0].priceAfter)
        assertEquals("2026-08-12", items[0].effectiveDate)
        assertNull(items[1].effectiveDate) // 적용일자 공란 (DOC-016)
    }

    @Test
    fun `이미지가 아니면 실패로 알린다`() {
        assumeOcrInstalled()

        assertFailsWith<IllegalArgumentException> { extractor.extract("이미지가 아닌 바이트".toByteArray()) }
    }

    @Test
    fun `실행 파일이 없으면 사용할 수 없다고 알린다 - 예외가 아니다`() {
        val missing = ImageTextExtractor(command = "tesseract-not-installed", languages = "kor", timeoutSeconds = 5)

        assertEquals(false, missing.isAvailable)
    }
}
