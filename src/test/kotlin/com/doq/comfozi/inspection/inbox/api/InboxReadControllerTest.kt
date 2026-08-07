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

    /** 세션 하나를 구조화해 인박스를 만들고 inboxId를 돌려준다. */
    private fun structuredInboxId(): Long {
        val session = ingestionService.createFromManualRecords(
            listOf(manualInput(docId = "DOC-1"), manualInput(docId = "DOC-2")),
        )
        structuringService.struct(session.id!!)
        return inboxRepository.findByIngestionId(session.id!!)!!.id!!
    }

    @Test
    fun `GET inbox {id} - 인박스 상세 + 항목`() {
        val inboxId = structuredInboxId()

        mockMvc.perform(get("/api/inbox/$inboxId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.inboxId").value(inboxId))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].observed.docId").value("DOC-1"))
            .andExpect(jsonPath("$.data.items[0].current.docId").value("DOC-1"))
            .andExpect(jsonPath("$.data.items[0].status").value("NEW"))
    }

    @Test
    fun `GET inbox - 목록에 인박스가 포함된다`() {
        val inboxId = structuredInboxId()

        mockMvc.perform(get("/api/inbox"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[?(@.inboxId == $inboxId)]").exists())
    }

    @Test
    fun `GET inbox {id} - 없으면 404`() {
        mockMvc.perform(get("/api/inbox/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }
}
