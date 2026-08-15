package com.doq.comfozi.structuring.normalization

import org.springframework.stereotype.Component

/**
 * 금액 표기 정규화 — 사람이 읽는 표기를 숫자 문자열 하나로 모은다 ("86,000   " · "9,800원" → "86000" · "9800").
 *
 * 취합 표의 셀과 문서 추출 결과는 원문 표기를 그대로 물고 온다. 그대로 두면 두 군데가 깨진다.
 *  - 검수 편집(PATCH)은 금액을 `Long` 으로 받는다. 화면이 받은 값을 그대로 되돌려 보내므로,
 *    금액을 건드리지 않고 저장만 눌러도 자기가 만든 값 때문에 막힌다.
 *  - 중복 판정 키에 금액이 들어간다([com.doq.comfozi.structuring.mapping.MappedRecord.duplicateKeyValues]).
 *    "86,000" 과 "86000" 이 다른 값으로 취급돼 같은 행을 중복으로 못 잡는다.
 *
 * 숫자를 못 뽑으면 원문을 그대로 둔다 — 조용히 비우면 원인이 사라진 채 필수값 누락으로 둔갑한다.
 * 검수자가 화면에서 이상한 표기를 보고 고치는 편이 낫다.
 */
@Component
class PriceNormalizer {

    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val stripped = raw.replace(DECORATION, "")
        if (stripped.isEmpty()) return raw

        // "1,234.00" 처럼 소수점이 붙어도 실제로 정수면 받아 준다. 진짜 소수(1234.56)는 원문을 남긴다.
        val value = stripped.toLongOrNull()
            ?: stripped.toBigDecimalOrNull()?.takeIf { it.stripTrailingZeros().scale() <= 0 }?.toLong()

        return value?.toString() ?: raw
    }

    private companion object {
        /** 자릿수 쉼표·공백·통화 표기 — 금액의 뜻을 바꾸지 않는 장식이라 떼어낸다. */
        val DECORATION = Regex("""[,\s원₩]""")
    }
}
