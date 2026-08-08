package com.doq.comfozi.inspection.service

import com.doq.comfozi.inspection.api.ExportRow
import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InspectionExportServiceImpl(
    private val inspectionRepository: InspectionRepository,
    private val recordRepository: InspectionRecordRepository,
) : InspectionExportService {

    @Transactional(readOnly = true)
    override fun exportRows(inspectionId: Long): List<ExportRow> {
        if (!inspectionRepository.existsById(inspectionId)) {
            throw NoSuchElementException("알 수 없는 Inspection $inspectionId")
        }
        return recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
            .filter { it.status == InspectionRecordStatus.CONFIRMED } // 승인 항목만 (보류·반려·미승인 제외)
            .map { ExportRow.from(it.current) } // 사람이 승인한 편집본
    }
}
