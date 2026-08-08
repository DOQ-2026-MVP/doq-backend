package com.doq.comfozi.inspection.repository

import com.doq.comfozi.inspection.domain.InspectionChangeLog
import org.springframework.data.jpa.repository.JpaRepository

interface InspectionChangeLogRepository : JpaRepository<InspectionChangeLog, Long> {
    fun findByRecordIdOrderByIdAsc(recordId: Long): List<InspectionChangeLog>

    /** 여러 레코드의 이력을 시각순으로 한 번에 (export 임베드용). */
    fun findByRecordIdInOrderByIdAsc(recordIds: Collection<Long>): List<InspectionChangeLog>
}
