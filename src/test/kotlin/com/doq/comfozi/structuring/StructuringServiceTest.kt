package com.doq.comfozi.structuring

import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.repository.IngestionRepository
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@RecordApplicationEvents
class StructuringServiceTest(
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val ingestionRepository: IngestionRepository,
) {

    @Test
    fun `struct는 세션 레코드마다 StructuredRecord를 발행한다`(events: ApplicationEvents) {
        val session = ingestionService.createFromManualRecords(
            listOf(manualInput("A"), manualInput("B")),
        )

        structuringService.struct(session.id!!) // DRAFT에서 바로 구조화

        val published = events.stream(StructuredRecord::class.java).toList()
        assertEquals(2, published.size)
        assertEquals(setOf("A", "B"), published.mapNotNull { it.observed.docId }.toSet())
        assertEquals(IngestionStatus.STRUCTURED, ingestionRepository.findByIdOrNull(session.id!!)!!.status)
    }
}
