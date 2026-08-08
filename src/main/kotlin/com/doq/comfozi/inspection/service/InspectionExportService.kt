package com.doq.comfozi.inspection.service

import com.doq.comfozi.inspection.api.ExportRow

/** 검수 결과 export — 승인(CONFIRMED)된 레코드만 ComfoziAI 전달 형식으로 뽑는다(요구사항 §7). */
interface InspectionExportService {

    /** [inspectionId] 검수의 CONFIRMED 레코드를 export 행으로(없으면 404, 승인 0건이면 빈 목록). */
    fun exportRows(inspectionId: Long): List<ExportRow>
}
