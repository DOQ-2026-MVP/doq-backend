package com.doq.comfozi.structuring.ingestion.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@RecordApplicationEvents
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionEventTest(
    @Autowired val service: IngestionService,
) {

    @Test
    fun `수기 입력 시 IngestionRecordsAppended가 발행된다`(events: ApplicationEvents) {
        val session = service.createFromManualRecords(
            listOf(IngestionManualInput(docId = "E-1", rawItemName = "임시")),
        )

        val published = events.stream(IngestionRecordsAppended::class.java).toList()
        assertEquals(1, published.size)
        assertEquals(session.id, published[0].ingestionId)
        assertTrue(published[0].records.isNotEmpty())
    }
}
