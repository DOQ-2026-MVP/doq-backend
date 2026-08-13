package com.doq.comfozi.ingestion.api

import com.doq.comfozi.structuring.StructuringService
import com.doq.comfozi.ingestion.awaitParsed
import com.doq.comfozi.ingestion.domain.IngestionUploadStatus
import com.doq.comfozi.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import com.doq.comfozi.ingestion.service.IngestionFileInput
import com.doq.comfozi.ingestion.service.IngestionService
import com.doq.common.config.AsyncConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 파싱이 실제로 **업로드 응답 뒤에** 도는지 확인한다.
 *
 * 워커를 1개로 묶고 앞선 작업으로 붙잡아 두면 파싱이 큐에서 대기하므로, "접수는 됐고 파싱은 아직"인
 * 중간 상태를 흔들림 없이 관찰할 수 있다. (다른 테스트들은 `awaitParsed` 로 끝날 때까지 기다린다.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "app.storage.local.root=build/test-uploads",
        "app.ingestion.parse.core-pool-size=1",
        "app.ingestion.parse.max-pool-size=1",
    ],
)
class IngestionUploadParseAsyncTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val service: IngestionService,
    @Autowired val structuringService: StructuringService,
    @Autowired val uploadRepository: IngestionUploadRepository,
    @Autowired val recordRepository: IngestionRecordRepository,
    @Autowired @Qualifier(AsyncConfig.INGESTION_PARSE_EXECUTOR) val parseExecutor: ThreadPoolTaskExecutor,
) {

    private val csv = """
        문서ID,원본유형,공급사,원문 품목명,규격,단위,기존단가(원),변경단가(원),적용일
        DOC-001,PDF,가온푸드,토마토살사S/O,4kg/PK,PK,32000,33600,2026-08-01
    """.trimIndent()

    /** 워커를 붙잡아 두는 빗장 — 열기 전까지 뒤따르는 파싱은 큐에서 대기한다. */
    private val gate = CountDownLatch(1)

    @AfterTest
    fun release() = gate.countDown() // 테스트가 중간에 실패해도 워커를 풀어 준다

    /** 하나뿐인 워커 스레드를 점유한다. */
    private fun blockWorker() {
        val occupied = CountDownLatch(1)
        parseExecutor.execute {
            occupied.countDown()
            gate.await()
        }
        assertTrue(occupied.await(5, TimeUnit.SECONDS), "워커 점유 실패")
    }

    /**
     * 밀린 파싱이 다 끝날 때까지 기다린다 — 워커가 1개라 뒤에 넣은 작업이 돌았다면 앞선 파싱도 끝났다.
     * (업로드가 지워져 상태 전이가 남지 않는 경우처럼 `awaitParsed` 로는 볼 수 없는 완료를 기다릴 때 쓴다.)
     */
    private fun awaitWorkerIdle() {
        val done = CountDownLatch(1)
        parseExecutor.execute { done.countDown() }
        assertTrue(done.await(10, TimeUnit.SECONDS), "워커 대기열이 비워지지 않음")
    }

    private fun upload(fileName: String = "test.csv"): Long {
        val input = IngestionFileInput(fileName, "text/csv", csv.byteInputStream())

        return service.ingestFile(input).id!!
    }

    private fun uploadStatus(ingestionId: Long) =
        uploadRepository.findByIngestionIdOrderByIdAsc(ingestionId).single().status

    @Test
    fun `업로드는 파싱을 기다리지 않는다 - PARSING 으로 접수됐다가 PARSED 로 끝난다`() {
        blockWorker()
        val id = upload()

        // 워커가 붙잡혀 있으므로 접수만 된 상태 — 행은 아직 없다
        assertEquals(IngestionUploadStatus.PARSING, uploadStatus(id))
        assertTrue(recordRepository.findByIngestionIdOrderByIdAsc(id).isEmpty())

        gate.countDown()
        uploadRepository.awaitParsed(id)

        assertEquals(IngestionUploadStatus.PARSED, uploadStatus(id))
        assertEquals(1, recordRepository.findByIngestionIdOrderByIdAsc(id).size)
    }

    @Test
    fun `파싱 중인 세션은 구조화할 수 없다 - 반쪽짜리 세션을 넘기지 않는다`() {
        blockWorker()
        val id = upload()

        assertFailsWith<IllegalStateException> { structuringService.struct(id) }
    }

    @Test
    fun `파싱 대기 중 업로드를 지우면 행이 만들어지지 않는다`() {
        blockWorker()
        val id = upload()
        val uploadId = uploadRepository.findByIngestionIdOrderByIdAsc(id).single().id!!

        mockMvc.perform(delete("/api/ingestion/$id/uploads/$uploadId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("DRAFT"))

        gate.countDown()
        awaitWorkerIdle()

        assertTrue(uploadRepository.findByIngestionIdOrderByIdAsc(id).isEmpty())
        assertTrue(recordRepository.findByIngestionIdOrderByIdAsc(id).isEmpty()) // 유령 행 없음
    }
}
