package com.doq.comfozi.structuring.detection

import com.doq.comfozi.structuring.mapping.MappedRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnomalyRuleBasedDetectorTest {

    private val detector = AnomalyRuleBasedDetector()

    /** 캐노니컬 관찰값 헬퍼 — 기본은 이상 없는 정상값. */
    private fun observed(
        docId: String = "DOC-001",
        supplier: String = "가온푸드",
        normalizedItemName: String = "토마토 살사 소스",
        spec: String = "4kg/PK",
        unit: String = "PK",
        priceBefore: String = "32000",
        priceAfter: String = "33600",
        effectiveDate: String = "2026-08-01",
    ) = MappedRecord(
        docId = docId,
        sourceType = "PDF",
        supplier = supplier,
        rawItemName = "토마토살사S/O",
        normalizedItemName = normalizedItemName,
        spec = spec,
        unit = unit,
        priceBefore = priceBefore,
        priceAfter = priceAfter,
        effectiveDate = effectiveDate,
    )

    @Test
    fun `정상 레코드는 플래그 없음`() {
        assertEquals(emptySet(), detector.detect(listOf(observed())).single())
    }

    @Test
    fun `적용일 공란은 필수값 누락`() {
        val flags = detector.detect(listOf(observed(effectiveDate = ""))).single()
        assertTrue(AnomalyRuleBasedFlag.MISSING_REQUIRED in flags)
    }

    @Test
    fun `규격 기존-변경 패턴은 규격 불일치`() {
        val flags = detector.detect(listOf(observed(spec = "기존 10kg / 변경 9kg"))).single()
        assertTrue(AnomalyRuleBasedFlag.SPEC_MISMATCH in flags)
    }

    @Test
    fun `표준 밖 또는 복수 단위는 단위 불일치`() {
        assertTrue(AnomalyRuleBasedFlag.UNIT_MISMATCH in detector.detect(listOf(observed(unit = "KG/단"))).single())
        assertTrue(AnomalyRuleBasedFlag.UNIT_MISMATCH !in detector.detect(listOf(observed(unit = "BOX"))).single())
    }

    @Test
    fun `detectPerRecord - per-record 3종만 산출하고 중복은 다루지 않는다`() {
        assertEquals(emptySet(), detector.detectPerRecord(observed())) // 정상
        assertEquals(
            setOf(AnomalyRuleBasedFlag.SPEC_MISMATCH, AnomalyRuleBasedFlag.UNIT_MISMATCH),
            detector.detectPerRecord(observed(spec = "기존 1kg / 변경 4단", unit = "KG/단")),
        )
        assertTrue(AnomalyRuleBasedFlag.MISSING_REQUIRED in detector.detectPerRecord(observed(effectiveDate = "")))
    }

    @Test
    fun `중복키 일치 시 docId 오름차순 그룹의 2번째 이후만 중복 의심`() {
        val a = observed(docId = "DOC-018") // 입력 순서상 먼저지만 docId가 큼
        val b = observed(docId = "DOC-017") // 기준(더 작은 docId)

        val flags = detector.detect(listOf(a, b))

        assertTrue(AnomalyRuleBasedFlag.DUPLICATE_SUSPECTED in flags[0]) // DOC-018 = 중복
        assertTrue(AnomalyRuleBasedFlag.DUPLICATE_SUSPECTED !in flags[1]) // DOC-017 = 기준
    }
}
