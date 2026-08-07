package com.doq.comfozi.inspection.inbox.repository

import com.doq.comfozi.inspection.inbox.domain.Inbox
import org.springframework.data.jpa.repository.JpaRepository

interface InboxRepository : JpaRepository<Inbox, Long> {
    fun findByIngestionId(ingestionId: Long): Inbox?
}
