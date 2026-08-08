package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.OffsetDateTime

/**
 * ComfoziAI 전달용 export 행 — 승인된 레코드의 편집본을 규칙명세 §7 권장 스키마(영문 snake_case field ID)로.
 *
 * JSON은 이 구조 그대로, CSV는 [InspectionCsvWriter]가 평탄화한다. 필드 설명은 README 참고.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ExportRow(
    val docId: String?,
    val sourceType: String?,
    val supplierName: String?,
    val rawItemName: String?,
    val normalizedItemName: String?,
    val spec: String?,
    val unit: String?,
    val priceBefore: Int?,
    val priceAfter: Int?,
    val effectiveDate: String?,
    val reviewStatus: String,
    val exceptionFlags: List<String>,
    val sourceRef: ExportSourceRef,
    val reviewedAt: OffsetDateTime?,
    val reviewMemo: String,
    val changeLog: List<ExportChangeLogEntry>,
)

/** 원본 근거 — 파일이면 파일명·행번호, 수기면 input_method만. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ExportSourceRef(
    val inputMethod: String, // "file" | "manual"
    val fileName: String?,
    val rowNo: Int?,
)

/** 변경 이력 1건(평탄) — 편집이면 필드 diff, 전이면 review_status 변화. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ExportChangeLogEntry(
    val at: OffsetDateTime,
    val field: String?,
    val from: String?,
    val to: String?,
    val action: String, // "edit" | "confirm" | "reject"
)

/** export 스키마 매핑 — 상태·플래그·가격·필드ID를 권장 스키마 값으로 변환하는 단일 출처. */
object ExportSchema {

    /** 검수 상태 → review_status 값. */
    fun reviewStatus(status: InspectionRecordStatus): String = when (status) {
        InspectionRecordStatus.NEW -> "new"
        InspectionRecordStatus.CONFIRMED -> "approved"
        InspectionRecordStatus.REJECTED -> "rejected"
    }

    /** 이상 플래그 → exception_flags 값(missing_required 등). */
    fun flag(flag: AnomalyRuleBasedFlag): String = flag.name.lowercase()

    /** 단가 문자열(콤마 허용) → 정수. 비었거나 파싱 불가면 null. */
    fun price(raw: String?): Int? = raw?.replace(",", "")?.trim()?.toIntOrNull()

    /** MappedRecord 프로퍼티명 → export field ID (change_log 필드명 통일용). */
    fun fieldId(mappedRecordProperty: String): String = FIELD_ID[mappedRecordProperty] ?: mappedRecordProperty

    private val FIELD_ID: Map<String, String> = mapOf(
        "docId" to "doc_id",
        "sourceType" to "source_type",
        "supplier" to "supplier_name",
        "rawItemName" to "raw_item_name",
        "normalizedItemName" to "normalized_item_name",
        "spec" to "spec",
        "unit" to "unit",
        "priceBefore" to "price_before",
        "priceAfter" to "price_after",
        "effectiveDate" to "effective_date",
    )
}

/**
 * [ExportRow] 목록을 UTF-8 CSV(RFC 4180)로 — 헤더 1행 + 데이터. 엑셀 호환 BOM 선두.
 * source_ref는 컬럼으로 평탄화(source_*), exception_flags는 `|`로 join. change_log는 JSON 전용(CSV 제외).
 */
object InspectionCsvWriter {

    private val COLUMNS: List<Pair<String, (ExportRow) -> String?>> = listOf(
        "doc_id" to { r -> r.docId },
        "source_type" to { r -> r.sourceType },
        "supplier_name" to { r -> r.supplierName },
        "raw_item_name" to { r -> r.rawItemName },
        "normalized_item_name" to { r -> r.normalizedItemName },
        "spec" to { r -> r.spec },
        "unit" to { r -> r.unit },
        "price_before" to { r -> r.priceBefore?.toString() },
        "price_after" to { r -> r.priceAfter?.toString() },
        "effective_date" to { r -> r.effectiveDate },
        "review_status" to { r -> r.reviewStatus },
        "exception_flags" to { r -> r.exceptionFlags.joinToString("|") },
        "source_input_method" to { r -> r.sourceRef.inputMethod },
        "source_file_name" to { r -> r.sourceRef.fileName },
        "source_row_no" to { r -> r.sourceRef.rowNo?.toString() },
        "reviewed_at" to { r -> r.reviewedAt?.toString() },
        "review_memo" to { r -> r.reviewMemo },
    )

    fun render(rows: List<ExportRow>): String {
        val sb = StringBuilder("﻿") // UTF-8 BOM
        sb.append(COLUMNS.joinToString(",") { it.first }).append("\r\n") // 헤더 (0건이어도 나감)
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
