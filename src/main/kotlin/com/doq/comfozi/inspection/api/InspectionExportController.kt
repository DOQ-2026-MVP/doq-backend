package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.service.InspectionExportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 검수 결과 export API — 승인(CONFIRMED)된 레코드만 ComfoziAI 전달 형식으로 내려준다(요구사항 §7).
 *
 * 포맷별 엔드포인트(JSON/CSV)로 분리, 각각 파일 다운로드(Content-Disposition). 공통 응답 envelope를
 * 쓰지 않고 **원본 파일 그대로** 내려준다(전달용 산출물). 승인 0건이면 빈 배열 / 헤더만 있는 CSV.
 */
@Tag(name = "검수 결과 export", description = "승인 항목을 JSON·CSV로 내보내는 API")
@RestController
@RequestMapping("/api/inspection")
class InspectionExportController(
    private val service: InspectionExportService,
) {

    /** 승인 레코드 JSON 배열 다운로드. 없으면 404. */
    @Operation(summary = "검수 결과 JSON export (승인 항목만)")
    @GetMapping("/{inspectionId}/export.json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun exportJson(
        @PathVariable inspectionId: Long,
    ): ResponseEntity<List<ExportRow>> =
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, attachment(inspectionId, "json"))
            .body(service.exportRows(inspectionId))

    /** 승인 레코드 UTF-8 CSV 다운로드(헤더 1행 포함). 없으면 404. */
    @Operation(summary = "검수 결과 CSV export (승인 항목만)")
    @GetMapping("/{inspectionId}/export.csv", produces = [CSV_CONTENT_TYPE])
    fun exportCsv(
        @PathVariable inspectionId: Long,
    ): ResponseEntity<ByteArray> {
        val csv = InspectionCsvWriter.render(service.exportRows(inspectionId))
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, attachment(inspectionId, "csv"))
            .contentType(MediaType.parseMediaType(CSV_CONTENT_TYPE))
            .body(csv.toByteArray(Charsets.UTF_8))
    }

    private fun attachment(inspectionId: Long, ext: String) =
        "attachment; filename=\"inspection-$inspectionId.$ext\""

    companion object {
        private const val CSV_CONTENT_TYPE = "text/csv; charset=UTF-8"
    }
}
