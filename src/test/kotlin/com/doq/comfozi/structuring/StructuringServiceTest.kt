package com.doq.comfozi.structuring

import com.doq.comfozi.structuring.ingestion.service.IngestionManualInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@RecordApplicationEvents
class StructuringServiceTest(
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
) {

    @Test
    fun `struct는 세션 레코드마다 RecordStructured를 발행한다`(events: ApplicationEvents) {
        val session = ingestionService.createFromManualRecords(
            listOf(
                IngestionManualInput(docId = "A", rawItemName = "가"),
                IngestionManualInput(docId = "B", rawItemName = "나"),
            ),
        )

        structuringService.struct(session.id!!)

        val published = events.stream(RecordStructured::class.java).toList()
        assertEquals(2, published.size)
        assertEquals(setOf("A", "B"), published.mapNotNull { it.observed["docId"] }.toSet())
    }
}
