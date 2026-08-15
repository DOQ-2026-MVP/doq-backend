package com.doq.comfozi.structuring.normalization

import kotlin.test.Test
import kotlin.test.assertEquals

class PriceNormalizerTest {

    private val normalizer = PriceNormalizer()

    @Test
    fun `자릿수 쉼표와 꼬리 공백을 떼어 숫자 문자열로`() {
        // 취합 표 셀에서 실제로 나온 모양
        assertEquals("86000", normalizer.normalize("86,000       "))
        assertEquals("32000", normalizer.normalize(" 32,000"))
    }

    @Test
    fun `통화 표기가 붙어도 숫자만 남긴다`() {
        // 문서 추출(PDF·이미지)이 물고 오는 모양
        assertEquals("9800", normalizer.normalize("9,800원"))
        assertEquals("10486", normalizer.normalize("10,486 원"))
        assertEquals("1500", normalizer.normalize("₩1,500"))
    }

    @Test
    fun `이미 깨끗한 값은 그대로 둔다`() {
        assertEquals("32000", normalizer.normalize("32000"))
        assertEquals("0", normalizer.normalize("0"))
    }

    @Test
    fun `실제로 정수인 소수 표기는 받아 준다`() {
        assertEquals("1234", normalizer.normalize("1,234.00"))
    }

    /**
     * 못 읽는 값을 비우면 원인이 사라진 채 "필수값 누락"으로 둔갑한다.
     * 원문을 남겨야 검수자가 화면에서 무엇이 이상한지 보고 고친다.
     */
    @Test
    fun `숫자를 못 뽑으면 원문을 남긴다`() {
        assertEquals("추후 안내", normalizer.normalize("추후 안내"))
        assertEquals("1,234.56", normalizer.normalize("1,234.56"))
        assertEquals("", normalizer.normalize(""))
        assertEquals(null, normalizer.normalize(null))
    }
}
