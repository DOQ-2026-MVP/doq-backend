package com.doq.comfozi.ingestion.extraction

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

/**
 * 테스트용 PDF 생성 — 요구사항이 준 원본 문서는 `docs/requirements/` 가 통째로 gitignore 라
 * 픽스처로 커밋할 수 없다. 대신 아는 내용을 담은 PDF 를 그때그때 만들어 쓴다.
 *
 * 실제 원본으로 하는 회귀는 로컬 수동 검증 몫이다(README·PR 참고).
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
