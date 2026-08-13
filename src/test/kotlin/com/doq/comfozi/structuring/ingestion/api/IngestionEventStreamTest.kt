package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.awaitParsed
import com.doq.comfozi.structuring.ingestion.manualInput
import com.doq.comfozi.structuring.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.structuring.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.structuring.ingestion.service.IngestionFileInput
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import com.doq.common.config.AppObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * 세션 현황 스트림(SSE) — 어떤 파일이 올라와 어디까지 처리됐는지, 수기 행이 저장됐는지가 폴링 없이 흐르는가.
 *
 * 전달이 비동기라 단언 전에 [awaitStream] 으로 해당 조각이 도착할 때까지 기다린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.storage.local.root=build/test-uploads"])
class IngestionEventStreamTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val uploadRepository: IngestionUploadRepository,
    @Autowired val recordRepository: IngestionRecordRepository,
) {

    private val header = "문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원),적용일"
    private val csv = "$header\nDOC-001,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,32000,33600,2026-08-01"

    /** 구독 시작 — 응답은 열린 채로 남고, 지금까지 쓰인 내용은 [MvcResult] 로 계속 읽는다. */
    private fun subscribe(ingestionId: Long): MvcResult =
        mockMvc.perform(get("/api/ingestion/$ingestionId/events"))
            .andExpect(request().asyncStarted())
            .andReturn()

    private fun body(result: MvcResult) = result.response.contentAsString

    /** 지금까지 온 이벤트들 (오래된 것부터). */
    private fun events(result: MvcResult): List<Map<*, *>> =
        body(result).lineSequence()
            .filter { it.startsWith("data:") }
            .map { AppObjectMapper.instance.readValue(it.removePrefix("data:"), Map::class.java) }
            .toList()

    /** 스트림에 [expected] 가 나타날 때까지 기다렸다가 그때까지의 이벤트들을 돌려준다. */
    private fun awaitStream(result: MvcResult, expected: String, timeoutMillis: Long = 10_000): List<Map<*, *>> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        do {
            if (body(result).contains(expected)) return events(result)
            Thread.sleep(10)
        } while (System.currentTimeMillis() < deadline)

        fail("스트림에 '$expected' 가 ${timeoutMillis}ms 안에 오지 않음. 받은 내용:\n${body(result)}")
    }

    /** 이벤트가 실어 나르는 현황 — 변경 응답이 돌려주는 것과 같은 타입이다. */
    private fun Map<*, *>.state() = this["state"] as Map<*, *>
    private fun Map<*, *>.upload(i: Int) = (state()["uploads"] as List<*>)[i] as Map<*, *>
    private fun Map<*, *>.manuals() = state()["manuals"] as List<*>
    private fun Map<*, *>.change() = this["change"] as Map<*, *>

    @Test
    fun `구독하면 현재 현황이 스냅샷으로 먼저 온다 - 최초 조회를 따로 하지 않아도 된다`() {
        val id = service.ingestManual(listOf(manualInput(docId = "MAN-1"))).id!!

        val snapshot = awaitStream(subscribe(id), "event:state").single()

        assertEquals(id.toInt(), snapshot.state()["ingestionId"])
        assertEquals("DRAFT", snapshot.state()["status"])
        assertEquals(1, snapshot.manuals().size)
        assertNull(snapshot["change"]) // 스냅샷은 계기가 없다
    }

    @Test
    fun `업로드는 파일 이름·상태와 함께 접수·처리 완료가 차례로 흐른다`() {
        val id = service.createSession().id!!
        val stream = subscribe(id)

        service.ingestFile(IngestionFileInput("test.csv", "text/csv", csv.byteInputStream()), id)
        uploadRepository.awaitParsed(id)

        val received = awaitStream(stream, "UPLOAD_SETTLED")
        assertEquals(
            listOf("UPLOAD_RECEIVED", "UPLOAD_SETTLED"),
            received.drop(1).map { it.change()["type"] }, // 첫 건은 스냅샷
        )

        val settled = received.last().upload(0)
        assertEquals("test.csv", settled["fileName"])
        assertEquals("PARSED", settled["status"])
        assertNull(settled["failureReason"])
    }

    @Test
    fun `파싱 실패도 사유와 함께 흐른다`() {
        val id = service.createSession().id!!
        val stream = subscribe(id)

        val input = IngestionFileInput("bad.csv", "text/csv", "이름,수량\n연필,3".byteInputStream())
        service.ingestFile(input, id)
        uploadRepository.awaitParsed(id)

        val failed = awaitStream(stream, "PARSE_FAILED").last().upload(0)
        assertEquals("bad.csv", failed["fileName"])
        assertContains(failed["failureReason"] as String, "필수 헤더 누락") // 한글이 깨지지 않는다
        assertEquals("text/event-stream;charset=UTF-8", stream.response.contentType)
    }

    @Test
    fun `수기 행 저장도 흐른다 - 화면이 목록을 그릴 id·생성시각과 함께`() {
        val id = service.createSession().id!!
        val stream = subscribe(id)

        service.ingestManual(listOf(manualInput(docId = "MAN-1"), manualInput(docId = "MAN-2")), id)

        val last = awaitStream(stream, "RECORDS_ADDED").last()
        assertEquals(2, last.change()["addedCount"])

        val records = last.manuals().map { it as Map<*, *> }
        assertEquals(2, records.size)
        records.forEach {
            assertNotNull(it["id"])
            assertNotNull(it["createdAt"])
        }
        val ids = records.map { it["id"] as Int }
        assertEquals(ids.sorted(), ids) // id 오름차순
    }

    @Test
    fun `파일에서 나온 행은 이벤트에 담기지 않는다 - 큰 파일에도 이벤트가 커지지 않는다`() {
        val rows = (1..500).joinToString("\n") { "DOC-$it,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,32000,33600,2026-08-01" }
        val id = service.createSession().id!!
        val stream = subscribe(id)

        val input = IngestionFileInput("big.csv", "text/csv", "$header\n$rows".byteInputStream())
        service.ingestFile(input, id)
        uploadRepository.awaitParsed(id)

        val last = awaitStream(stream, "UPLOAD_SETTLED").last()
        assertEquals("PARSED", last.upload(0)["status"])
        assertEquals(emptyList<Any>(), last.manuals()) // 파일 행은 수기 목록에 없다
        assertEquals(false, body(stream).contains("DOC-1")) // 행 원문도 없다
    }

    @Test
    fun `다른 세션의 변화는 흐르지 않는다`() {
        val mine = service.createSession().id!!
        val other = service.createSession().id!!
        val stream = subscribe(mine)

        service.ingestManual(listOf(manualInput(docId = "OTHER")), other)
        service.ingestManual(listOf(manualInput(docId = "MINE")), mine)

        val received = awaitStream(stream, "RECORDS_ADDED")
        assertEquals(setOf(mine.toInt()), received.map { it.state()["ingestionId"] }.toSet())
        assertEquals(1, received.last().manuals().size) // 남의 행이 섞이지 않았다
    }

    @Test
    fun `수기 행 수정도 흐른다`() {
        val id = service.ingestManual(listOf(manualInput(docId = "MAN-1"))).id!!
        val recordId = recordRepository.findByIngestionIdOrderByIdAsc(id).single().id!!
        val stream = subscribe(id)

        service.updateManualRecord(id, recordId, manualInput(docId = "MAN-1", rawItemName = "고친품목"))

        val last = awaitStream(stream, "RECORD_UPDATED").last()
        assertEquals(recordId.toInt(), last.change()["recordId"])

        val manual = last.manuals().single() as Map<*, *>
        assertEquals("고친품목", (manual["content"] as Map<*, *>)["rawItemName"]) // 고쳐진 원문이 실린다
    }

    @Test
    fun `행 삭제도 흐른다`() {
        val id = service.ingestManual(listOf(manualInput(docId = "MAN-1"), manualInput(docId = "MAN-2"))).id!!
        val recordId = recordRepository.findByIngestionIdOrderByIdAsc(id).first().id!!
        val stream = subscribe(id)

        service.deleteRecord(id, recordId)

        val last = awaitStream(stream, "RECORD_DELETED").last()
        assertEquals(recordId.toInt(), last.change()["recordId"])
        assertEquals(1, last.manuals().size) // 지운 행이 빠진 현황
    }

    @Test
    fun `업로드 삭제도 흐른다`() {
        val id = service.createSession().id!!
        val input = IngestionFileInput("test.csv", "text/csv", csv.byteInputStream())
        service.ingestFile(input, id)
        uploadRepository.awaitParsed(id)
        val uploadId = uploadRepository.findByIngestionIdOrderByIdAsc(id).single().id!!
        val stream = subscribe(id)

        service.deleteUpload(id, uploadId)

        val last = awaitStream(stream, "UPLOAD_DELETED").last()
        assertEquals(uploadId.toInt(), last.change()["uploadId"])
        assertEquals(emptyList<Any>(), last.state()["uploads"]) // 지운 뒤 현황
    }

    @Test
    fun `세션 비우기도 흐른다`() {
        val id = service.ingestManual(listOf(manualInput(docId = "MAN-1"))).id!!
        val input = IngestionFileInput("test.csv", "text/csv", csv.byteInputStream())
        service.ingestFile(input, id)
        uploadRepository.awaitParsed(id)
        val stream = subscribe(id)

        service.truncate(id)

        val last = awaitStream(stream, "SESSION_CLEARED").last()
        assertEquals(emptyList<Any>(), last.state()["uploads"])
        assertEquals(emptyList<Any>(), last.manuals())
        assertEquals("DRAFT", last.state()["status"])
    }

    @Test
    fun `없는 세션 구독은 404 - 연결을 열지 않는다`() {
        // EventSource 가 실제로 보내는 Accept 로 확인한다 — 이 헤더에서 JSON 본문은 협상에 걸린다
        mockMvc.perform(get("/api/ingestion/999999/events").header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE))
            .andExpect(status().isNotFound)
            .andExpect(request().asyncNotStarted())
    }
}
