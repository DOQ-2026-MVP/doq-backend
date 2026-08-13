package com.doq.comfozi.structuring.ingestion.support

import org.springframework.stereotype.Component

/**
 * 원본 문서(FILE) 형식 검증 — PDF·이미지만 보관 대상으로 받는다.
 *
 * [IngestionUploadBatchFileParser]의 분류와 같은 원칙으로 **매직 바이트를 먼저** 본다.
 * 확장자만 바꾼 파일을 걸러내기 위함이며, 확장자는 매직 바이트가 없을 때의 근거가 아니라
 * 아예 쓰지 않는다(원본 보관은 내용이 곧 계약).
 *
 * 취합 파일(CSV/XLSX)과 달리 **내용을 파싱하지 않는다** — 행 추출은 아직 지원하지 않고
 * 보관 + 수기 입력으로 보완한다.
 */
@Component
class IngestionDocumentValidator {

    /** 보관 대상 원본 문서 형식과 그 매직 바이트. */
    private enum class DocumentFormat(val label: String, val magic: ByteArray) {
        PDF("PDF", byteArrayOf(0x25, 0x50, 0x44, 0x46)), // %PDF
        PNG("PNG", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)), // \x89PNG
        JPEG("JPEG", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())),
    }

    /** 지원 형식이 아니면 예외(400). 지원하면 형식 이름을 돌려준다. */
    fun validate(bytes: ByteArray): String {
        val format = DocumentFormat.entries.firstOrNull { bytes.startsWith(it.magic) }
        requireNotNull(format) {
            "지원하지 않는 문서 형식입니다 (PDF·PNG·JPEG만 가능). 취합 표 파일은 /uploads 로 업로드하세요."
        }
        return format.label
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
