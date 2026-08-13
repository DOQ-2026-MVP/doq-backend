package com.doq.comfozi.structuring.ingestion.service

import com.doq.comfozi.structuring.ingestion.domain.Ingestion
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUpload

/** 인입 조회 서비스 — 세션·업로드·원본 행 읽기. */
interface IngestionReadService {

    /** 세션 조회 — 없으면 예외. */
    fun getSession(ingestionId: Long): Ingestion

    /** 세션의 업로드들 (id 오름차순). 수기 입력은 업로드가 아니므로 포함되지 않는다. */
    fun getUploads(ingestionId: Long): List<IngestionUpload>

    /** 세션에 속한 업로드 1건 — 없거나 다른 세션의 업로드면 없는 것으로 취급(404). */
    fun getUpload(ingestionId: Long, uploadId: Long): IngestionUpload

    /** 세션의 원본 행들 (id 오름차순). */
    fun getRecords(ingestionId: Long): List<IngestionRecord>
}
