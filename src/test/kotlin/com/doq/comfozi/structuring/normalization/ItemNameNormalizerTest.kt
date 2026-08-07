package com.doq.comfozi.structuring.normalization

import kotlin.test.Test
import kotlin.test.assertEquals

class ItemNameNormalizerTest {

    private val normalizer = ItemNameNormalizer()

    @Test
    fun `사전에 있으면 정규화 품목명을 반환한다`() {
        assertEquals("냉동 감자튀김", normalizer.normalize("냉감튀2K"))
        assertEquals("토마토 살사 소스", normalizer.normalize("토마토살사S/O"))
        assertEquals("아보카도", normalizer.normalize("아보카도30입"))
    }

    @Test
    fun `사전에 없으면 데이터 부족`() {
        assertEquals(ItemNameNormalizer.INSUFFICIENT_DATA, normalizer.normalize("없는품목XYZ"))
    }

    @Test
    fun `공란-null 이면 데이터 부족`() {
        assertEquals(ItemNameNormalizer.INSUFFICIENT_DATA, normalizer.normalize(null))
        assertEquals(ItemNameNormalizer.INSUFFICIENT_DATA, normalizer.normalize("  "))
    }
}
