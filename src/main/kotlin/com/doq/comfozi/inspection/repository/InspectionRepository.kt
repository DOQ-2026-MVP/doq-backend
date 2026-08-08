package com.doq.comfozi.inspection.repository

import com.doq.comfozi.inspection.domain.Inspection
import org.springframework.data.jpa.repository.JpaRepository

interface InspectionRepository : JpaRepository<Inspection, Long> {
    fun findByIngestionId(ingestionId: Long): Inspection?
}
