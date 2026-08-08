package com.doq.comfozi.inspection.api

import com.doq.comfozi.structuring.mapping.MappedRecord

/**
 * ComfoziAI 전달용 export 행 — 승인된 레코드의 편집본(current)을 **영문 field ID**로 정렬(요구사항 §7).
 *
 * 필드 순서·이름이 JSON 키이자 CSV 컬럼이며, README 필드 설명의 단일 출처. (골든 데이터 컬럼 순서에 맞춤)
 */
data class ExportRow(
    val docId: String?,
    val sourceType: String?,
    val supplier: String?,
    val rawItemName: String?,
    val normalizedItemName: String?,
    val spec: String?,
    val unit: String?,
    val priceBefore: String?,
    val priceAfter: String?,
    val effectiveDate: String?,
) {
    companion object {
        fun from(current: MappedRecord) = ExportRow(
            docId = current.docId,
            sourceType = current.sourceType,
            supplier = current.supplier,
            rawItemName = current.rawItemName,
            normalizedItemName = current.normalizedItemName,
            spec = current.spec,
            unit = current.unit,
            priceBefore = current.priceBefore,
            priceAfter = current.priceAfter,
            effectiveDate = current.effectiveDate,
        )
    }
}

/** [ExportRow] 목록을 UTF-8 CSV(RFC 4180)로 렌더링 — 헤더 1행 + 데이터. 엑셀 호환 위해 BOM 선두. */
object InspectionCsvWriter {

    /** 컬럼 순서·헤더명 = [ExportRow] 필드. JSON 키와 동일하게 맞춘다. */
    private val COLUMNS: List<Pair<String, (ExportRow) -> String?>> = listOf(
        "docId" to ExportRow::docId,
        "sourceType" to ExportRow::sourceType,
        "supplier" to ExportRow::supplier,
        "rawItemName" to ExportRow::rawItemName,
        "normalizedItemName" to ExportRow::normalizedItemName,
        "spec" to ExportRow::spec,
        "unit" to ExportRow::unit,
        "priceBefore" to ExportRow::priceBefore,
        "priceAfter" to ExportRow::priceAfter,
        "effectiveDate" to ExportRow::effectiveDate,
    )

    fun render(rows: List<ExportRow>): String {
        val sb = StringBuilder("﻿") // UTF-8 BOM
        sb.append(COLUMNS.joinToString(",") { it.first }).append("\r\n") // 헤더 (0건이어도 헤더는 나감)
        rows.forEach { row ->
            sb.append(COLUMNS.joinToString(",") { escape(it.second(row)) }).append("\r\n")
        }
        return sb.toString()
    }

    /** 쉼표·따옴표·개행 포함 시 큰따옴표로 감싸고 내부 따옴표는 이스케이프. */
    private fun escape(value: String?): String {
        val s = value ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
    }
}
