package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.service.IngestionManualInput
import com.doq.comfozi.structuring.ingestion.service.IngestionReadService
import com.doq.comfozi.structuring.ingestion.service.IngestionRecordsAppended
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
class IngestionSseHubTest(
    @Autowired val hub: IngestionSseHub,
    @Autowired val service: IngestionService,
    @Autowired val readService: IngestionReadService,
) {

    /** send를 가로채 전송 횟수를 기록하는 테스트용 emitter. */
    private class RecordingSseEmitter : SseEmitter() {
        val sent = mutableListOf<Any>()
        override fun send(builder: SseEventBuilder) {
            sent += builder
        }
    }

    @Test
    fun `구독자에게 추가된 행이 record 이벤트로 전송된다`() {
        val session = service.createFromManualRecords(
            listOf(IngestionManualInput(docId = "OBS-1", rawItemName = "임시")),
        )
        val records = readService.getRecords(session.id!!)
        val emitter = RecordingSseEmitter()
        hub.register(session.id!!, emitter)

        hub.onRecordsAppended(IngestionRecordsAppended(session.id!!, records))

        assertEquals(records.size, emitter.sent.size)
    }

    @Test
    fun `다른 세션 이벤트는 전송되지 않는다`() {
        val emitter = RecordingSseEmitter()
        hub.register(1L, emitter)

        hub.onRecordsAppended(IngestionRecordsAppended(999L, emptyList()))

        assertEquals(0, emitter.sent.size)
    }
}
