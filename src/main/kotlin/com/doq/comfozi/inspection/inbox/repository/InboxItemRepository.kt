package com.doq.comfozi.inspection.inbox.repository

import com.doq.comfozi.inspection.inbox.domain.InboxItem
import org.springframework.data.jpa.repository.JpaRepository

interface InboxItemRepository : JpaRepository<InboxItem, Long> {
    fun findByInboxIdOrderByIdAsc(inboxId: Long): List<InboxItem>
}
