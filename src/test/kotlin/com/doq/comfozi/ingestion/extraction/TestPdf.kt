package com.doq.comfozi.ingestion.extraction

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

/**
 * 테스트용 PDF 생성 — 내용을 통제해야 하는 경우(빈 텍스트·특정 문구)에 쓴다.
 *
 * 제공된 실제 공문 2개는 `src/test/resources/fixtures/notice-*.pdf` 로 들어와 있다
 * (`docs/requirements/additional_inputs/` 에서 복사). 실제 문서의 표 레이아웃·한글 추출을
 * 확인하는 쪽은 그걸 쓰고, 여기서 만든 PDF 는 경계 조건용이다.
 */
object TestPdf {

    /** [lines] 를 담은 1쪽짜리 PDF. 텍스트 레이어가 있으므로 [PdfTextExtractor] 로 다시 읽힌다. */
    fun of(vararg lines: String): ByteArray = PDDocument().use { doc ->
        val page = PDPage()
        doc.addPage(page)

        PDPageContentStream(doc, page).use { content ->
            content.beginText()
            content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            content.setLeading(16f)
            content.newLineAtOffset(50f, 700f)
            lines.forEach {
                content.showText(it)
                content.newLine()
            }
            content.endText()
        }

        ByteArrayOutputStream().use { out ->
            doc.save(out)
            out.toByteArray()
        }
    }

    /** 텍스트 레이어가 없는 PDF (빈 쪽) — 스캔본처럼 글자를 못 뽑는 경우. */
    fun blank(): ByteArray = PDDocument().use { doc ->
        doc.addPage(PDPage())
        ByteArrayOutputStream().use { out ->
            doc.save(out)
            out.toByteArray()
        }
    }
}
