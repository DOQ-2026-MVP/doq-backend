package com.doq.comfozi.ingestion.extraction

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component

/**
 * PDF 원본 → 텍스트. 항목 해석은 하지 않고 **글자만** 꺼낸다(해석은 [ItemExtractor]).
 *
 * 요구사항이 제공한 PDF 4개는 전부 인쇄 산출물이라 텍스트 레이어가 온전하다 — 표 셀 값까지
 * 텍스트 추출만으로 나오므로 OCR 이 필요 없다. 반대로 **스캔본처럼 텍스트가 없는 PDF 는 여기서
 * 걸러진다**: 빈 텍스트를 넘겨 LLM 이 지어내게 두는 것보다, 못 읽었다고 실패로 남기는 편이 낫다.
 */
@Component
class PdfTextExtractor {

    fun extract(pdfBytes: ByteArray): String {
        val text = try {
            Loader.loadPDF(pdfBytes).use { PDFTextStripper().getText(it) }
        } catch (e: Exception) {
            throw IllegalArgumentException("PDF 를 열 수 없습니다", e)
        }

        require(text.isNotBlank()) {
            "텍스트를 읽을 수 없는 PDF 입니다 (스캔 이미지로만 된 문서는 아직 지원하지 않습니다)"
        }
        return text
    }
}
