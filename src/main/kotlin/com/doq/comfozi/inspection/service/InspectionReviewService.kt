package com.doq.comfozi.inspection.service

import com.doq.comfozi.inspection.domain.InspectionRecord
import com.doq.comfozi.structuring.mapping.MappedRecord

/** 검수 쓰기 오케스트레이터 — 검수자가 레코드를 교정·확정·반려·초기화한다. */
interface InspectionReviewService {

    /**
     * [inspectionRecordId] 레코드의 편집본을 [values]로, 메모를 [memo]로 교체(없으면 404, 확정된 레코드면 409).
     * EDIT 이력을 남긴다. 메모는 값과 함께 전체 교체 — null 이면 비워진다.
     */
    fun edit(inspectionRecordId: Long, values: MappedRecord, memo: String? = null): InspectionRecord

    /** [inspectionRecordId] 레코드 확정(없으면 404, 필수값 누락이면 409). 이미 확정이면 멱등. CONFIRM 이력을 남긴다. */
    fun confirm(inspectionRecordId: Long): InspectionRecord

    /** [inspectionRecordId] 레코드 반려(없으면 404). 이미 반려면 멱등. REJECT 이력을 남긴다. */
    fun reject(inspectionRecordId: Long): InspectionRecord

    /**
     * [inspectionRecordId] 레코드를 검수 전(NEW) 상태로 초기화(없으면 404) — 편집본을 관찰값으로 되돌리고
     * 메모를 지운다. 확정된 레코드에도 쓸 수 있다(잠금 해제 포함). 플래그는 되돌린 값으로 재평가하고
     * RESET 이력(되돌린 필드 diff 포함)을 남긴다.
     */
    fun reset(inspectionRecordId: Long): InspectionRecord

    /**
     * [inspectionId] 검수의 남은 NEW 레코드를 일괄 확정(없으면 404). 필수값이 누락된 레코드는
     * 확정하지 않고 건너뛴다(요구사항 §6, 승인 차단). 확정/차단 건수를 돌려준다.
     */
    fun confirmAll(inspectionId: Long): BulkConfirmResult
}

/** 일괄 확정 결과 — [confirmedCount] 확정됨, [blockedCount] 필수값 누락으로 건너뜀. */
data class BulkConfirmResult(
    val confirmedCount: Int,
    val blockedCount: Int,
)
