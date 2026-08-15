package com.doq.comfozi.inspection.api

import com.doq.comfozi.inspection.service.InspectionReviewService
import com.doq.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 검수(Inspection) 쓰기 API — 검수자가 레코드를 교정·확정·반려한다.
 *
 * 레코드 단위 편집/확정/반려 + 검수 단위 일괄 확정. 대상이 없으면 404, 확정된 레코드 편집은 409(편집 잠금).
 */
@Tag(name = "검수(Inspection)", description = "구조화 결과를 사람이 검수·확정하는 쓰기 API")
@RestController
@RequestMapping("/api/inspection")
class InspectionReviewController(
    private val service: InspectionReviewService,
) {

    /** 레코드 편집본(current) 교체. 확정된 레코드면 409. */
    @Operation(summary = "검수 레코드 편집 (current 교체)")
    @PatchMapping("/records/{inspectionRecordId}")
    fun edit(
        @PathVariable inspectionRecordId: Long,
        @RequestBody request: @Valid InspectionRecordEditRequest,
    ): ApiResponse<InspectionRecordResponse> =
        ApiResponse.ok(InspectionRecordResponse(service.edit(inspectionRecordId, request.toMappedRecord())))

    /** 레코드 확정 (NEW/REJECTED → CONFIRMED). 선택적 메모를 이력에 남긴다. */
    @Operation(summary = "검수 레코드 확정")
    @PostMapping("/records/{inspectionRecordId}/confirm")
    fun confirm(
        @PathVariable inspectionRecordId: Long,
        @RequestBody(required = false) request: InspectionActionRequest?,
    ): ApiResponse<InspectionRecordResponse> =
        ApiResponse.ok(InspectionRecordResponse(service.confirm(inspectionRecordId, request?.memo)))

    /** 레코드 반려 (→ REJECTED). 확정 레코드의 편집 잠금을 푸는 경로. 선택적 메모를 이력에 남긴다. */
    @Operation(summary = "검수 레코드 반려")
    @PostMapping("/records/{inspectionRecordId}/reject")
    fun reject(
        @PathVariable inspectionRecordId: Long,
        @RequestBody(required = false) request: InspectionActionRequest?,
    ): ApiResponse<InspectionRecordResponse> =
        ApiResponse.ok(InspectionRecordResponse(service.reject(inspectionRecordId, request?.memo)))

    /** 검수 단위 일괄 확정 — 남은 NEW 레코드 전체를 확정한다(필수값 누락은 건너뜀). */
    @Operation(summary = "검수 일괄 확정 (남은 NEW 레코드 전체, 필수값 누락은 건너뜀)")
    @PostMapping("/{inspectionId}/confirm")
    fun confirmAll(
        @PathVariable inspectionId: Long,
    ): ApiResponse<InspectionBulkConfirmResponse> {
        val result = service.confirmAll(inspectionId)
        return ApiResponse.ok(
            InspectionBulkConfirmResponse(
                inspectionId = inspectionId,
                confirmedCount = result.confirmedCount,
                blockedCount = result.blockedCount,
            ),
        )
    }
}
