package com.doq.comfozi.inspection.inbox.api

import com.doq.comfozi.inspection.inbox.repository.InboxRepository
import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
class InboxReadControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val ingestionService: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val inboxRepository: InboxRepository,
) {

    /** 세션 하나를 구조화해 인박스를 만들고 (ingestionId, inboxId)를 돌려준다. */
    private fun structured(): Pair<Long, Long> {
        val session = ingestionService.createFromManualRecords(
            listOf(manualInput(docId = "DOC-1"), manualInput(docId = "DOC-2")),
        )
        structuringService.struct(session.id!!)
        val inbox = inboxRepository.findByIngestionId(session.id!!)!!
        return session.id!! to inbox.id!!
    }

    @Test
    fun `GET inbox {id} - inboxId로 상세 + 항목`() {
        val (_, inboxId) = structured()

        mockMvc.perform(get("/api/inbox/$inboxId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.inboxId").value(inboxId))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].observed.docId").value("DOC-1"))
            .andExpect(jsonPath("$.data.items[0].status").value("NEW"))
    }

    @Test
    fun `GET inbox - ingestionId로 상세 + 항목`() {
        val (ingestionId, inboxId) = structured()

        mockMvc.perform(get("/api/inbox").param("ingestionId", ingestionId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.inboxId").value(inboxId))
            .andExpect(jsonPath("$.data.ingestionId").value(ingestionId))
            .andExpect(jsonPath("$.data.items.length()").value(2))
    }

    @Test
    fun `GET inbox {id} - 없으면 404`() {
        mockMvc.perform(get("/api/inbox/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `GET inbox - 없는 ingestionId면 404`() {
        mockMvc.perform(get("/api/inbox").param("ingestionId", "999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
