package com.doq.comfozi.structuring

import com.doq.comfozi.ingestion.domain.IngestionStatus
import com.doq.comfozi.ingestion.manualInput
import com.doq.comfozi.ingestion.repository.IngestionRepository
import com.doq.comfozi.ingestion.service.IngestionService
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 인계를 요청 트랜잭션에서 떼어내면서 잃은 원자성을 무엇으로 대신하는지 고정한다.
 *
 * 핵심 주장: 인계가 실패하면 **아무것도 커밋되지 않고** 세션이 FAILED 로 남아, 기존 재시도
 * (`POST /api/structuring/{id}`)로 온전히 복구된다 — 인박스가 두 개 생기지도 않는다.
 */
@SpringBootTest
class StructuringHandoffTest(
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val ingestionRepository: IngestionRepository,
    @Autowired val inspectionRepository: InspectionRepository,
    @Autowired val inspectionRecordRepository: InspectionRecordRepository,
    @Autowired val failOnce: FailOnceIntakeListener,
) {

    /** 인계 트랜잭션 안에서 한 번만 터지는 리스너 — 적재 실패를 재현한다. */
    class FailOnceIntakeListener {
        private val armed = AtomicBoolean(false)

        fun arm() = armed.set(true)

        @EventListener
        fun on(event: StructuredRecords) {
            check(!armed.compareAndSet(true, false)) { "인계 실패 재현" }
        }
    }

    @TestConfiguration
    class Config {
        @Bean
        fun failOnceIntakeListener() = FailOnceIntakeListener()
    }

    private fun draftSession(): Long =
        ingestionService.ingestManual(listOf(manualInput(docId = "A"), manualInput(docId = "B"))).id!!

    @Test
    fun `인계가 실패하면 아무것도 남지 않고 세션은 FAILED 로 재시도를 기다린다`() {
        val id = draftSession()
        failOnce.arm()

        structuringService.struct(id) // 계산은 성공 — 실패는 커밋 이후 인계에서 난다

        ingestionRepository.awaitStatus(id, IngestionStatus.FAILED)
        assertNull(inspectionRepository.findByIngestionId(id)) // 인박스는 롤백돼 없다
    }

    @Test
    fun `실패 후 재시도하면 복구되고 인박스는 하나만 생긴다`() {
        val id = draftSession()
        failOnce.arm()
        structuringService.struct(id)
        ingestionRepository.awaitStatus(id, IngestionStatus.FAILED)

        structuringService.struct(id) // FAILED 에서 재시도

        val inspection = inspectionRepository.awaitInspection(id)
        assertEquals(IngestionStatus.STRUCTURED, ingestionRepository.findById(id).get().status)
        assertEquals(2, inspectionRecordRepository.findByInspectionIdOrderByIdAsc(inspection.id!!).size)
    }
}
