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
 * 취합 파일(BATCH_FILE) 파서 — 포맷을 분류한 뒤 CSV(Apache Commons CSV) / XLSX(Apache POI)로
 * 원문을 헤더 + 행으로 분해하기만 한다. 값 검증·정규화는 하지 않음(후속 structuring).
 */
@Component
class IngestionUploadBatchFileParser {
    /** 취합 파일 포맷. */
    private enum class BatchFileFormat { CSV, XLSX }

    fun parse(fileName: String?, bytes: ByteArray): IngestionUploadBatchFileContent =
        when (classify(fileName, bytes)) {
            BatchFileFormat.CSV -> parseCsv(bytes)
            BatchFileFormat.XLSX -> parseXlsx(bytes)
        }

    /** 매직 바이트(XLSX=zip `PK`) 우선, 없으면 확장자로 판별. */
    private fun classify(fileName: String?, bytes: ByteArray): BatchFileFormat {
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            return BatchFileFormat.XLSX
        }
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        return if (ext == "xlsx" || ext == "xls") BatchFileFormat.XLSX else BatchFileFormat.CSV
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
