package com.doq.comfozi.inspection.inbox

import com.doq.comfozi.inspection.inbox.domain.InboxItemStatus
import com.doq.comfozi.inspection.inbox.repository.InboxItemRepository
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * struct → inspection 인계가 InboxItem으로 영속되는지 (동기 same-tx) 확인.
 * jsonb(MappedRecord·flags) 저장/복원 왕복도 함께 검증한다.
 */
@SpringBootTest
class InboxIntakeTest(
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val inboxItemRepository: InboxItemRepository,
) {

    @Test
    fun `struct 결과가 InboxItem으로 영속된다`() {
        val session = ingestionService.createFromManualRecords(
            listOf(manualInput(docId = "DOC-1"), manualInput(docId = "DOC-2")),
        )

        structuringService.struct(session.id!!)

        val items = inboxItemRepository.findByIngestionIdOrderByIdAsc(session.id!!)
        assertEquals(2, items.size)

        val item = items.first()
        assertEquals("DOC-1", item.observed.docId)
        assertEquals("DOC-1", item.current.docId) // 편집본 = observed 복사
        assertEquals(session.id, item.ingestionId)
        assertEquals(InboxItemStatus.NEW, item.status)
        // flags jsonb 왕복: 두 레코드는 중복이라 2번째(DOC-2)에 duplicate 플래그
        assertEquals(setOf(AnomalyRuleBasedFlag.DUPLICATE_SUSPECTED), items[1].flags)
    }
}
