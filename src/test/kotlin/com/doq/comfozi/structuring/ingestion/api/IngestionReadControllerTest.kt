package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.service.IngestionManualInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
class IngestionReadControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
) {

    @Test
    fun `GET 세션은 세션과 원본 행들을 반환한다`() {
        val id = service.createFromManualRecords(
            listOf(IngestionManualInput(docId = "MAN-9", rawItemName = "임시")),
        ).id!!

        mockMvc.perform(get("/api/ingestion/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.ingestionId").value(id))
            .andExpect(jsonPath("$.data.records[0].docId").value("MAN-9"))
    }

    @Test
    fun `GET 없는 세션은 404`() {
        mockMvc.perform(get("/api/ingestion/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `SSE 라이브 스트림 구독은 async로 열린다`() {
        val id = service.createSession().id!!

        mockMvc.perform(get("/api/ingestion/$id/records/stream").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
    }

    @Test
    fun `SSE 없는 세션은 404`() {
        mockMvc.perform(get("/api/ingestion/999999/records/stream"))
            .andExpect(status().isNotFound)
    }
}
