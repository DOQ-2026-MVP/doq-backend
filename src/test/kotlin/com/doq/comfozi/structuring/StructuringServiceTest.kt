package com.doq.comfozi.structuring

import com.doq.comfozi.ingestion.domain.IngestionStatus
import com.doq.comfozi.ingestion.manualInput
import com.doq.comfozi.ingestion.repository.IngestionRepository
import com.doq.comfozi.ingestion.service.IngestionService
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
    fun `struct는 세션 구조화 완료본을 배치 1개로 인계한다`(events: ApplicationEvents) {
        val session = ingestionService.ingestManual(
            listOf(manualInput("A"), manualInput("B")),
        )

        structuringService.struct(session.id!!) // DRAFT에서 바로 구조화

        // 인계 지시는 요청 트랜잭션에서 동기 발행된다 (검수로 가는 StructuredRecords 는 커밋 이후 별도 스레드)
        val published = events.stream(StructuringComputed::class.java).toList()
        assertEquals(1, published.size) // 세션당 배치 이벤트 1개
        val records = published.first().records
        assertEquals(2, records.size)
        assertEquals(setOf("A", "B"), records.mapNotNull { it.observed.docId }.toSet())

        ingestionRepository.awaitStructured(session.id!!) // 인계가 끝나야 STRUCTURED
    }
}
