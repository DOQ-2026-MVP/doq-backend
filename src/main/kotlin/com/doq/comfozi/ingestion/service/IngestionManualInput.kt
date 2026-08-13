package com.doq.comfozi.ingestion.service

import com.doq.comfozi.ingestion.domain.IngestionContent
import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.common.config.AppObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import java.time.LocalDate

/**
 * 수기 입력 값 — 요구사항 §입력 파일 컬럼(9개). 경계에서 검증을 통과한 완성값이라 전 필드 non-null.
 */
data class IngestionManualInput(
    val docId: String,
    val sourceType: String,
    val supplier: String,
    val rawItemName: String,
    val spec: String,
    val unit: String,
    val priceBefore: Long,
    val priceAfter: Long,
    val effectiveDate: LocalDate,
) {

    /**
     * 원문(jsonb)으로 변환. 소스 무관 캐노니컬 **문자열** 맵 — 키는 프로퍼티명, 값은 문자열로
     * ObjectMapper가 직렬화한다(키 하드코딩 없음, 숫자·날짜는 문자열로 변환).
     */
    fun toContent(): IngestionContent =
        IngestionContent(AppObjectMapper.instance.convertValue(this, jacksonTypeRef<Map<String, String?>>()))

    /** 원본 행 엔티티로 변환. 업로드 출처가 없으므로 [IngestionRecord.uploadRef]는 null. */
    fun toEntity(ingestionId: Long): IngestionRecord =
        IngestionRecord(
            ingestionId = ingestionId,
            uploadRef = null,
            content = toContent(),
        )
}
