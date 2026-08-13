package com.doq.comfozi.ingestion.support

import org.springframework.stereotype.Component
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * 업로드 파일 분류 — 바이트만 보고 **어떤 처리 경로로 갈 파일인지** 판정한다.
 *
 * 업로드 엔드포인트가 하나뿐이라 이 판정이 곧 처리 분기다. 판정만 하고 파싱·저장은 하지 않는다
 * (파싱은 [IngestionUploadBatchFileParser]).
 *
 * **확장자는 보지 않는다** — 내용이 곧 계약이고, 확장자만 바꾼 파일이 엉뚱한 경로로 가면 안 된다.
 * CSV 는 매직 바이트가 없으므로 **알려진 이진 포맷을 모두 배제한 뒤 마지막 후보**로만 인정하며,
 * 그마저도 텍스트로 읽히지 않으면 거부한다(임의 바이너리가 CSV 파서까지 흘러가지 않게).
 */
@Component
class IngestionFileClassifier {

    fun classify(bytes: ByteArray): ClassifiedFile {
        MAGIC.firstOrNull { (magic, _) -> bytes.startsWith(magic) }
            ?.let { (_, classified) -> return classified }

        // 알려진 이진 포맷이 아니면 CSV 후보 — 단, 텍스트로 읽혀야 한다
        require(looksLikeText(bytes)) {
            "지원하지 않는 파일 형식입니다 (CSV·XLSX·PDF·PNG·JPEG만 가능)"
        }
        return ClassifiedFile.BatchFile(BatchFileFormat.CSV)
    }

    /**
     * UTF-8 로 온전히 디코딩되고 NUL 이 없으면 텍스트로 본다.
     * 정확한 판별이 목적이 아니라 **이진 파일을 CSV 파서에 넘기지 않는 것**이 목적이다.
     */
    private fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.contains(0)) return false
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
            true
        } catch (_: CharacterCodingException) {
            false
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        /** 매직 바이트 → 처리 경로. 위에서부터 먼저 매칭된다. */
        val MAGIC: List<Pair<ByteArray, ClassifiedFile>> = listOf(
            byteArrayOf(0x50, 0x4B) to ClassifiedFile.BatchFile(BatchFileFormat.XLSX), // PK — zip(xlsx)
            byteArrayOf(0x25, 0x50, 0x44, 0x46) to ClassifiedFile.Document("PDF"), // %PDF
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) to ClassifiedFile.Document("PNG"), // \x89PNG
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) to ClassifiedFile.Document("JPEG"),
        )
    }
}

/** 분류 결과 — 업로드가 어느 처리 경로로 가는지. 경로가 늘면 컴파일러가 분기 누락을 잡아준다. */
sealed interface ClassifiedFile {

    /** 취합 표 파일 — 파싱해서 원본 행을 만든다. */
    data class BatchFile(val format: BatchFileFormat) : ClassifiedFile

    /** 원본 증빙 문서 — 보관만 하고 행은 만들지 않는다(행 추출 미지원). */
    data class Document(val format: String) : ClassifiedFile
}

/** 취합 표 파일의 포맷. */
enum class BatchFileFormat { CSV, XLSX }
