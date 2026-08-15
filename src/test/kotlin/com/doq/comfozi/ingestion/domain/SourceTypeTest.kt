package com.doq.comfozi.ingestion.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceTypeTest {

    @Test
    fun `확장자 표기는 범주로 흡수된다 - 같은 뜻이 갈라져 쌓이지 않게`() {
        for (raw in listOf("PNG", "JPG", "JPEG")) {
            assertEquals(SourceType.IMAGE, SourceType.from(raw), raw)
        }
    }

    @Test
    fun `대소문자와 앞뒤 공백은 흡수한다`() {
        assertEquals(SourceType.IMAGE, SourceType.from(" png "))
        assertEquals(SourceType.XLSX, SourceType.from("xlsx"))
        assertEquals(SourceType.MANUAL, SourceType.from("Manual"))
    }

    @Test
    fun `어휘 자체는 그대로 통과한다`() {
        for (type in SourceType.entries) {
            assertEquals(type, SourceType.from(type.name), type.name)
        }
    }

    @Test
    fun `모르는 값과 빈 값은 null - 판단은 호출부에 맡긴다`() {
        assertNull(SourceType.from("HWP"))
        assertNull(SourceType.from(""))
        assertNull(SourceType.from("   "))
        assertNull(SourceType.from(null))
    }

    @Test
    fun `normalizeOrKeep 은 아는 표기만 맞추고 나머지는 원문을 잃지 않는다`() {
        assertEquals("IMAGE", SourceType.normalizeOrKeep("PNG"))
        assertEquals("IMAGE", SourceType.normalizeOrKeep("jpeg"))
        assertEquals("XLSX", SourceType.normalizeOrKeep("xlsx"))
        // 모르는 값은 버리지 않는다 — 검수 화면이 낯선 값으로 보여주고 사람이 고친다
        assertEquals("HWP", SourceType.normalizeOrKeep("HWP"))
        assertNull(SourceType.normalizeOrKeep(null))
    }
}
