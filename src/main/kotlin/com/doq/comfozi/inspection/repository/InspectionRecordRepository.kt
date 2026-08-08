package com.doq.comfozi.inspection.repository

import com.doq.comfozi.inspection.domain.InspectionRecord
import org.springframework.data.jpa.repository.JpaRepository

interface InspectionRecordRepository : JpaRepository<InspectionRecord, Long> {
    fun findByInspectionIdOrderByIdAsc(inspectionId: Long): List<InspectionRecord>
}
