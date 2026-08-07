package com.doq.comfozi.structuring.detection

import com.doq.comfozi.structuring.mapping.MappedRecord
import org.springframework.stereotype.Component

/**
 * 규칙 기반 이상 탐지 (요구사항 §6) — [AnomalyRule] 목록을 조합해 레코드별 이상 플래그를 산출한다.
 *
 * 각 규칙이 "걸린 레코드 인덱스"를 돌려주면, 그걸 레코드별 플래그 집합으로 합친다.
 * 한 레코드가 여러 규칙에 걸리면 모두 표시.
 */
@Component
class AnomalyRuleBasedDetector : AnomalyDetector {

    override fun detect(observed: List<MappedRecord>): List<Set<AnomalyRuleBasedFlag>> {
        val perRecord = List(observed.size) { mutableSetOf<AnomalyRuleBasedFlag>() }

        AnomalyRule.ALL.forEach { rule ->
            rule.detect(observed).forEach { i -> perRecord[i] += rule.flag }
        }
        return perRecord
    }
}
