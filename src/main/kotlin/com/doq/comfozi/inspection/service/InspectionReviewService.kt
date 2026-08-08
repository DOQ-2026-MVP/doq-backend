package com.doq.comfozi.inspection.service

import com.doq.comfozi.inspection.domain.InspectionRecord
import com.doq.comfozi.structuring.mapping.MappedRecord

/** 검수 쓰기 오케스트레이터 — 검수자가 레코드를 교정·확정·반려한다. */
interface InspectionReviewService {

    /** [recordId] 레코드의 편집본을 [values]로 교체(없으면 404, 확정된 레코드면 409). */
    fun edit(recordId: Long, values: MappedRecord): InspectionRecord

    /** [recordId] 레코드 확정(없으면 404). 이미 확정이면 멱등. */
    fun confirm(recordId: Long): InspectionRecord

    /** [recordId] 레코드 반려(없으면 404). 이미 반려면 멱등. */
    fun reject(recordId: Long): InspectionRecord

    /** [inspectionId] 검수의 남은 NEW 레코드를 일괄 확정(없으면 404). 확정된 건수를 돌려준다. */
    fun confirmAll(inspectionId: Long): Int
}
