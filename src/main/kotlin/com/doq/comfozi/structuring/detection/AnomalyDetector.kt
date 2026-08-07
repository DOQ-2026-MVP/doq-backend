package com.doq.comfozi.structuring.detection

import com.doq.comfozi.structuring.mapping.MappedRecord

/**
 * 이상 탐지 — 세션 관찰값에서 레코드별 이상(anomaly) 플래그를 산출한다.
 *
 * 전략을 갈아끼울 수 있도록 인터페이스로 둔다. 현재 구현은 규칙 기반([AnomalyRuleBasedDetector]).
 */
interface AnomalyDetector {

    /** 세션 관찰값 리스트(입력 순서) → 레코드별 플래그(입력 순서 유지). */
    fun detect(observed: List<MappedRecord>): List<Set<AnomalyRuleBasedFlag>>
}
