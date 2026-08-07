package com.doq.comfozi.structuring

import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.mapping.MappedRecord

/**
 * 구조화 결과 **항목** — 원문 레코드 1건의 매핑·정규화·탐지 산출. [StructuredRecords]에 담겨 인계된다.
 */
data class StructuredRecord(
    val recordId: Long,
    // 원본 근거(SourceRef)용 — 파일 출처면 채워지고 수기면 null
    val uploadType: IngestionUploadType?,
    val rowNo: Int?,
    // 관찰값(시스템 구조화) — 매핑 + 정규화 결과
    val observed: MappedRecord,
    // 이상 플래그 (규칙 기반 탐지 결과)
    val flags: Set<AnomalyRuleBasedFlag>,
)
