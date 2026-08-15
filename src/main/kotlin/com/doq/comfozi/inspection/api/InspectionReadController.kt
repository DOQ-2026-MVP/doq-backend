package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.domain.Inspection
import com.doq.comfozi.inspection.repository.InspectionChangeLogRepository
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.repository.findByIdOrNull
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 검수(Inspection) 조회 API — 검수 상세(레코드 포함)를 **ingestionId** 또는 **inspectionId**로 읽는다. (편집/확정은 추후)
 */
@Tag(name = "검수(Inspection)", description = "구조화 결과를 사람이 검수하는 조회 API")
@RestController
@RequestMapping("/api/inspection")
class InspectionReadController(
    private val inspectionRepository: InspectionRepository,
    private val inspectionRecordRepository: InspectionRecordRepository,
    private val inspectionChangeLogRepository: InspectionChangeLogRepository,
) {

    /** 인입 세션(ingestionId)의 검수 상세 + 레코드. 없으면 404. */
    @Operation(summary = "검수 상세 조회 (ingestionId로)")
    @GetMapping
    fun getByIngestion(
        @RequestParam ingestionId: Long,
    ): ApiResponse<InspectionResponse> {
        val inspection = inspectionRepository.findByIngestionId(ingestionId)
            ?: throw NoSuchElementException("ingestionId=$ingestionId 의 검수가 없습니다")
        return ApiResponse.ok(detail(inspection))
    }

    /** 검수(inspectionId) 상세 + 레코드. 없으면 404. */
    @Operation(summary = "검수 상세 조회 (inspectionId로)")
    @GetMapping("/{inspectionId}")
    fun get(
        @PathVariable inspectionId: Long,
    ): ApiResponse<InspectionResponse> {
        val inspection = inspectionRepository.findByIdOrNull(inspectionId)
            ?: throw NoSuchElementException("알 수 없는 Inspection $inspectionId")
        return ApiResponse.ok(detail(inspection))
    }

    /** 검수 레코드([inspectionRecordId])의 변경 이력 — 편집·확정·반려를 시각순으로. 레코드 없으면 404. */
    @Operation(summary = "검수 레코드 변경 이력 조회")
    @GetMapping("/records/{inspectionRecordId}/changelog")
    fun changelog(
        @PathVariable inspectionRecordId: Long,
    ): ApiResponse<List<InspectionChangeLogResponse>> {
        if (!inspectionRecordRepository.existsById(inspectionRecordId)) {
            throw NoSuchElementException("알 수 없는 검수 레코드 $inspectionRecordId")
        }
        val logs = inspectionChangeLogRepository.findByInspectionRecordIdOrderByIdAsc(inspectionRecordId)
            .map(::InspectionChangeLogResponse)
        return ApiResponse.ok(logs)
    }

    private fun detail(inspection: Inspection): InspectionResponse {
        val records = inspectionRecordRepository.findByInspectionIdOrderByIdAsc(requireNotNull(inspection.id))
            .map(::InspectionRecordResponse)
        return InspectionResponse(inspection, records)
    }
}
