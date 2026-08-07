package com.doq.comfozi.structuring

import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.mapping.MappedRecord

/**
 * 구조화 결과 이벤트 — structuring이 원문 레코드 1건을 매핑·정규화·탐지해 산출한 결과.
 *
 * structuring → inspection 인계 계약. inspection(inbox)이 받아 InboxItem으로 영속한다.
 * (structuring이 **계산 완료본**을 실어 넘기고, inspection은 저장만 한다.)
 */
data class StructuredRecord(
    val ingestionId: Long,
    val recordId: Long,
    // 원본 근거(SourceRef)용 — 파일 출처면 채워지고 수기면 null
    val uploadType: IngestionUploadType?,
    val rowNo: Int?,
    // 관찰값(시스템 구조화) — 매핑 + 정규화 결과
    val observed: MappedRecord,
    // 이상 플래그 (규칙 기반 탐지 결과)
    val flags: Set<AnomalyRuleBasedFlag>,
)
