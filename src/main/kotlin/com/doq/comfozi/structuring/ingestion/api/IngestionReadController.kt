package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.service.IngestionReadService
import com.doq.comfozi.structuring.ingestion.support.FileStorage
import com.doq.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.nio.charset.StandardCharsets

/**
 * 인입 조회 API — 세션·업로드 현황·원본 행을 읽는다. (적재는 [IngestionController])
 */
@Tag(name = "인입(Ingestion)", description = "파일 업로드·수기 입력을 세션에 적재하는 인입 API")
@RestController
@RequestMapping("/api/ingestion")
class IngestionReadController(
    private val readService: IngestionReadService,
    private val fileStorage: FileStorage,
    private val eventStream: IngestionEventStream,
) {

    /**
     * 세션 현황 실시간 구독 (SSE) — 어떤 파일이 올라왔고 어디까지 처리됐는지, 수기 행이 저장됐는지를
     * 폴링 없이 받는다. 파싱·추출이 비동기라 업로드 응답만으로는 결과를 알 수 없어서 뚫은 통로다.
     *
     * `event: state` 하나만 흐르고, 매번 **그 시점 현황 전체**([IngestionStateEvent])가 실린다 —
     * 받는 쪽은 화면을 갈아끼우면 된다. 구독 즉시 현재 상태가 한 번 오므로(`change: null`) 최초 조회를
     * 따로 하지 않아도 되고, 끊겼다 붙어도 그 스냅샷이 곧 최신이라 놓친 이벤트를 메울 필요가 없다.
     *
     * 실리는 현황은 세션 조회가 돌려주는 것과 같다 — 스트림을 못 쓰는 상황이면 그쪽으로 폴백하면 된다.
     *
     * 없는 세션이면 **본문 없이 404**다. 다른 엔드포인트와 달리 공통 실패 envelope(JSON)을 쓰지 않는데,
     * EventSource 는 `Accept: text/event-stream` 만 보내서 JSON 본문이 협상에 걸리기 때문이다
     * (그대로 두면 404 가 아니라 500 이 나간다). 스트림에서는 상태 코드로 충분하다.
     */
    @Operation(summary = "세션 현황 실시간 구독 (SSE)")
    @GetMapping("/{ingestionId}/events", produces = [EVENT_STREAM_UTF8])
    fun streamEvents(
        @PathVariable ingestionId: Long,
    ): ResponseEntity<SseEmitter> {
        val emitter = try {
            eventStream.subscribe(ingestionId)
        } catch (_: NoSuchElementException) {
            return ResponseEntity.notFound().build()
        }

        // charset 은 여기서 붙여야 한다 — produces 는 매핑용이고, SseEmitter 는 비어 있을 때
        // charset 없는 text/event-stream 을 직접 세팅한다(그러면 응답이 ISO-8859-1 로 잡힌다)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(EVENT_STREAM_UTF8))
            .body(emitter)
    }

    /**
     * 인입 세션 현황 조회 — 올라온 파일들과 수기 행들. 없으면 404.
     *
     * 변경 응답·현황 스트림과 **같은 [IngestionState]** 를 돌려준다. 화면이 세션에 대해 다루는 모델은
     * 하나이고, 이 엔드포인트는 그 모델을 언제든 다시 받아오는 통로다(스트림을 못 쓰는 경우의 폴백).
     */
    @Operation(summary = "인입 세션 현황 조회 (업로드·수기 행)")
    @GetMapping("/{ingestionId}")
    fun getIngestionState(
        @PathVariable ingestionId: Long,
    ): ApiResponse<IngestionState> {
        val status = readService.getStatus(ingestionId)

        return ApiResponse.ok(data = IngestionState(status))
    }

    /**
     * 세션에 적재된 **원본 행 전부** 조회 (수기·파일) — 확인·디버깅용. 없는 세션이면 404.
     *
     * 현황([getIngestionState])은 화면이 쓰는 만큼만 주므로 파일에서 나온 행이 빠져 있다. 파싱이
     * 무엇을 만들어냈는지 눈으로 봐야 할 때 여기로 본다. 한 파일이 수만 행일 수 있어 화면 경로에는
     * 섞지 않고 별도로 둔다.
     */
    @Operation(summary = "세션의 원본 행 전부 조회 (수기·파일, 확인·디버깅용)")
    @GetMapping("/{ingestionId}/records")
    fun getRecords(
        @PathVariable ingestionId: Long,
    ): ApiResponse<List<IngestionRecordResponse>> {
        readService.getSession(ingestionId) // 없는 세션이면 빈 목록 대신 404
        val records = readService.getRecords(ingestionId)

        return ApiResponse.ok(data = records.map(::IngestionRecordResponse))
    }

    /**
     * 업로드 원본 다운로드 — 화면에서 PDF·이미지를 열어보고 수기 입력으로 옮기기 위한 경로.
     * export 처럼 envelope 없이 파일 바이트 그대로 내려준다.
     */
    @Operation(summary = "업로드 원본 파일 다운로드")
    @GetMapping("/{ingestionId}/uploads/{uploadId}/content")
    fun downloadUpload(
        @PathVariable ingestionId: Long,
        @PathVariable uploadId: Long,
    ): ResponseEntity<Resource> {
        val upload = readService.getUpload(ingestionId, uploadId)
        val contentType = upload.contentType
            ?.let(MediaType::parseMediaType)
            ?: MediaType.APPLICATION_OCTET_STREAM

        val headerValue = ContentDisposition.inline()
            .filename(upload.fileName, StandardCharsets.UTF_8)
            .build()

        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, headerValue.toString())
            .body(InputStreamResource(fileStorage.load(upload.storageKey)))
    }

    private companion object {
        /**
         * charset 을 명시한다 — 붙이지 않으면 응답 인코딩이 ISO-8859-1 로 잡혀 한글(파싱 실패 사유·파일명)이
         * 깨진다. 브라우저 EventSource 는 규격상 UTF-8 로 읽지만 그 외 클라이언트는 헤더를 믿는다.
         */
        const val EVENT_STREAM_UTF8 = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"
    }
}
