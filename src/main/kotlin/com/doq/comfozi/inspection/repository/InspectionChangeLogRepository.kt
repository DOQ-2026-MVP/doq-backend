package com.doq.comfozi.inspection.repository

import com.doq.comfozi.inspection.domain.InspectionChangeLog
import org.springframework.data.jpa.repository.JpaRepository

interface InspectionChangeLogRepository : JpaRepository<InspectionChangeLog, Long> {
    fun findByRecordIdOrderByIdAsc(recordId: Long): List<InspectionChangeLog>
}
