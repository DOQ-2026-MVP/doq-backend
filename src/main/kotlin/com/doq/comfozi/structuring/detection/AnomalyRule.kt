package com.doq.comfozi.structuring.detection

import com.doq.comfozi.structuring.mapping.MappedRecord

/**
 * 이상 탐지 규칙 (요구사항 §6). 규칙마다 자신이 담당하는 이상이 걸리는 **레코드 인덱스 집합**을 반환한다.
 *
 * per-record(누락·규격·단위)와 cross-record(중복)를 같은 시그니처로 통일 — [AnomalyRuleBasedDetector]가 조합.
 */
sealed class AnomalyRule(val flag: AnomalyRuleBasedFlag) {

    /** 이상이 감지된 레코드의 (입력 순서) 인덱스. */
    abstract fun detect(observed: List<MappedRecord>): Set<Int>

    /** 필수 9필드 중 하나라도 공란/공백. */
    data object RequiredKeys : AnomalyRule(AnomalyRuleBasedFlag.MISSING_REQUIRED) {
        override fun detect(observed: List<MappedRecord>): Set<Int> =
            observed.indicesWhere { r -> r.requiredValues().any { it.isNullOrBlank() } }
    }

    /** 규격이 `기존 … / 변경 …` 패턴 (규격 변경 통보 혼입). */
    data object SpecChangePattern : AnomalyRule(AnomalyRuleBasedFlag.SPEC_MISMATCH) {
        private val regex = Regex("^\\s*기존\\s*(.+?)\\s*/\\s*변경\\s*(.+?)\\s*\$")
        override fun detect(observed: List<MappedRecord>): Set<Int> =
            observed.indicesWhere { regex.matches(it.spec?.trim().orEmpty()) }
    }

    /** 단위가 표준집합(PK·BOX·EA·PO) 밖이거나 복수 단위 병기(`/`·`,`). */
    data object StandardUnits : AnomalyRule(AnomalyRuleBasedFlag.UNIT_MISMATCH) {
        private val standard = setOf("PK", "BOX", "EA", "PO")
        override fun detect(observed: List<MappedRecord>): Set<Int> =
            observed.indicesWhere { mismatch(it.unit) }

        private fun mismatch(unit: String?): Boolean {
            val u = unit?.trim().orEmpty()
            if (u.isEmpty()) return false // 공란은 필수값 누락 소관
            if (u.contains('/') || u.contains(',')) return true
            return u.uppercase() !in standard
        }
    }

    /** 중복키 완전 일치 그룹의 (docId 오름차순) 2번째 이후. */
    data object DuplicateKey : AnomalyRule(AnomalyRuleBasedFlag.DUPLICATE_SUSPECTED) {
        override fun detect(observed: List<MappedRecord>): Set<Int> {
            val seenKeys = mutableSetOf<String>()
            val dup = mutableSetOf<Int>()
            observed.withIndex()
                .sortedBy { it.value.docId.orEmpty() }
                .forEach { (originalIndex, r) ->
                    val key = r.duplicateKeyValues().joinToString("") { it.orEmpty() }
                    if (!seenKeys.add(key)) dup += originalIndex // 이미 본 키 = 2번째 이후
                }
            return dup
        }
    }

    companion object {
        /** 적용 목록 — detector가 조합. */
        val ALL: List<AnomalyRule> = listOf(RequiredKeys, SpecChangePattern, StandardUnits, DuplicateKey)
    }
}

/** 조건에 맞는 레코드의 (입력 순서) 인덱스 집합. */
private inline fun List<MappedRecord>.indicesWhere(
    predicate: (MappedRecord) -> Boolean,
): Set<Int> = withIndex().filter { predicate(it.value) }.mapTo(mutableSetOf()) { it.index }
