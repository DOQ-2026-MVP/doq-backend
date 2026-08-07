package com.doq.comfozi.inspection.inbox.api

import com.doq.comfozi.inspection.inbox.domain.Inbox
import com.doq.comfozi.inspection.inbox.repository.InboxItemRepository
import com.doq.comfozi.inspection.inbox.repository.InboxRepository
import com.doq.common.web.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.repository.findByIdOrNull
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 검수 인박스 조회 API — 인박스 상세(항목 포함)를 **ingestionId** 또는 **inboxId**로 읽는다. (편집/확정은 추후)
 */
@Tag(name = "검수 인박스(Inbox)", description = "구조화 결과를 사람이 검수하는 인박스 조회 API")
@RestController
@RequestMapping("/api/inbox")
class InboxReadController(
    private val inboxRepository: InboxRepository,
    private val inboxItemRepository: InboxItemRepository,
) {

    /** 인입 세션(ingestionId)의 인박스 상세 + 항목. 없으면 404. */
    @Operation(summary = "인박스 상세 조회 (ingestionId로)")
    @GetMapping
    fun getByIngestion(
        @RequestParam ingestionId: Long,
    ): ApiResponse<InboxResponse> {
        val inbox = inboxRepository.findByIngestionId(ingestionId)
            ?: throw NoSuchElementException("ingestionId=$ingestionId 의 인박스가 없습니다")
        return ApiResponse.ok(detail(inbox))
    }

    /** 인박스(inboxId) 상세 + 항목. 없으면 404. */
    @Operation(summary = "인박스 상세 조회 (inboxId로)")
    @GetMapping("/{inboxId}")
    fun get(
        @PathVariable inboxId: Long,
    ): ApiResponse<InboxResponse> {
        val inbox = inboxRepository.findByIdOrNull(inboxId)
            ?: throw NoSuchElementException("알 수 없는 Inbox $inboxId")
        return ApiResponse.ok(detail(inbox))
    }

    private fun detail(inbox: Inbox): InboxResponse {
        val items = inboxItemRepository.findByInboxIdOrderByIdAsc(requireNotNull(inbox.id)).map(::InboxItemResponse)
        return InboxResponse(inbox, items)
    }
}
