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

    /**
     * 편집본 1건 재평가 — per-record 규칙(누락·규격·단위)만 적용해 플래그를 산출한다.
     * 검수자가 값을 고친 뒤 플래그를 갱신하는 용도. 중복(cross-record)은 세션 전체가 필요해 다루지 않는다.
     */
    fun detectPerRecord(record: MappedRecord): Set<AnomalyRuleBasedFlag>

    /**
     * 세션 단위 중복 재평가 — 편집으로 바뀐 [records](입력 순서)의 현재값으로 중복 그룹을 다시 판정한다.
     * 한 레코드 편집이 형제의 중복 여부까지 바꾸므로 세션 전체를 함께 본다.
     * @return 중복 의심에 해당하는 레코드의 (입력 순서) 인덱스 집합.
     */
    fun detectDuplicates(records: List<MappedRecord>): Set<Int>
}
