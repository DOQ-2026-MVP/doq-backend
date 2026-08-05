package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord

/** 인입 조회 서비스 — 세션·원본 행 읽기. */
interface IngestionReadService {

    /** 세션 조회 — 없으면 예외. */
    fun getSession(ingestionId: Long): Ingestion

    /** 세션의 원본 행들 (id 오름차순). */
    fun getRecords(ingestionId: Long): List<IngestionRecord>
}
