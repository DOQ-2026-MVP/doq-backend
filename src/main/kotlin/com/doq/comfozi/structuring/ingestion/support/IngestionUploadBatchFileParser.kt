package com.doq.comfozi.structuring.ingestion.support

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadRef
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import org.apache.commons.csv.CSVFormat
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
/**
 * 취합 파일(BATCH_FILE) 파서 — CSV(Apache Commons CSV) / XLSX(Apache POI)로 원문을 헤더 + 행으로
 * 분해한다. **형식 검증**(필수 헤더 9개 존재)은 하되, 값 검증·정규화는 하지 않는다(후속 structuring).
 * 필수 헤더가 없으면 컬럼 매핑이 불가하므로 업로드를 거부한다.
 *
 * 포맷 판별은 [IngestionFileClassifier] 가 이미 끝냈으므로 여기서 다시 하지 않는다.
 */
@Component
class IngestionUploadBatchFileParser {

    fun parse(bytes: ByteArray, format: BatchFileFormat): IngestionUploadBatchFileContent {
        // zip 이지만 xlsx 가 아닌 파일(.docx 등)은 여기서 터진다 — 스택트레이스 대신 400으로 안내
        val content = try {
            when (format) {
                BatchFileFormat.CSV -> parseCsv(bytes)
                BatchFileFormat.XLSX -> parseXlsx(bytes)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("${format} 파일로 읽을 수 없습니다 (지원 형식: CSV·XLSX·PDF·PNG·JPEG)", e)
        }

        validateRequiredHeaders(content.header)
        return content
    }

    /** 필수 헤더(9개)가 모두 있는지 검증 — 없으면 매핑 불가라 업로드 거부(400). */
    private fun validateRequiredHeaders(header: List<String>) {
        val present = header.mapTo(HashSet(), BatchFileColumn::normalize)
        val missing = BatchFileColumn.entries.filterNot { it.normalized in present }
        require(missing.isEmpty()) { "필수 헤더 누락: ${missing.joinToString(", ") { it.label }}" }
    }

    private fun parseCsv(bytes: ByteArray): IngestionUploadBatchFileContent {
        InputStreamReader(ByteArrayInputStream(stripUtf8Bom(bytes)), StandardCharsets.UTF_8).use { reader ->
            CSVFormat.DEFAULT.parse(reader).use { parser ->
                val all = parser.records
                    .map { rec -> (0 until rec.size()).map { rec.get(it) } }
                    .filter { row -> row.any { it.isNotBlank() } }
                return toSheet(all)
            }
        }
    }

    private fun parseXlsx(bytes: ByteArray): IngestionUploadBatchFileContent {
        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0) ?: return IngestionUploadBatchFileContent(emptyList(), emptyList())
            val fmt = DataFormatter()
            val all = sheet.mapNotNull { row ->
                val last = row.lastCellNum.toInt()
                if (last < 0) return@mapNotNull null
                val cells = (0 until last).map { c -> row.getCell(c)?.let { fmt.formatCellValue(it).trim() } ?: "" }
                cells.takeIf { it.any(String::isNotBlank) }
            }
            return toSheet(all)
        }
    }

    private fun toSheet(all: List<List<String>>): IngestionUploadBatchFileContent =
        if (all.isEmpty()) IngestionUploadBatchFileContent(emptyList(), emptyList())
        else IngestionUploadBatchFileContent(header = all.first().map { it.trim() }, rows = all.drop(1))

    private fun stripUtf8Bom(bytes: ByteArray): ByteArray =
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
}
