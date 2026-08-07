package com.doq.comfozi.inspection.inbox.api

import com.doq.comfozi.inspection.inbox.repository.InboxItemRepository
import com.doq.comfozi.inspection.inbox.repository.InboxRepository
import com.doq.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.repository.findByIdOrNull
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 검수 인박스 조회 API — 인박스 목록과 상세(항목 포함)를 읽는다. (편집/확정은 추후)
 */
@Tag(name = "검수 인박스(Inbox)", description = "구조화 결과를 사람이 검수하는 인박스 조회 API")
@RestController
@RequestMapping("/api/inbox")
class InboxReadController(
    private val inboxRepository: InboxRepository,
    private val inboxItemRepository: InboxItemRepository,
) {

    /** 인박스 목록(요약). */
    @Operation(summary = "인박스 목록 조회")
    @GetMapping
    fun list(): ApiResponse<List<InboxSummaryResponse>> =
        ApiResponse.ok(inboxRepository.findAll().map(::InboxSummaryResponse))

    /** 인박스 상세 + 검수 항목. 없으면 404. */
    @Operation(summary = "인박스 상세 조회 (항목 포함)")
    @GetMapping("/{inboxId}")
    fun get(
        @PathVariable inboxId: Long,
    ): ApiResponse<InboxResponse> {
        val inbox = inboxRepository.findByIdOrNull(inboxId)
            ?: throw NoSuchElementException("알 수 없는 Inbox $inboxId")
        val items = inboxItemRepository.findByInboxIdOrderByIdAsc(inboxId).map(::InboxItemResponse)
        return ApiResponse.ok(InboxResponse(inbox, items))
    }
}
