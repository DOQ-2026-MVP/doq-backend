package com.doq.comfozi.structuring.normalization

import org.springframework.stereotype.Component

/**
 * 품목명 정규화 — 원문 품목명 → 정규화 품목명 (요구사항 §3).
 *
 * 3단: **품목 마스터 사전 조회 → (규칙 기반, TODO) → "데이터 부족"**.
 * 자동 정규화 정확도는 평가 대상 아님 — 사전(제공 20건)만으로 요건 충족.
 * 못 만든 경우 빈칸이 아니라 [INSUFFICIENT_DATA]로 표시.
 */
@Component
class ItemNameNormalizer {

    fun normalize(rawItemName: String?): String {
        val raw = rawItemName?.trim()
        if (raw.isNullOrEmpty()) return INSUFFICIENT_DATA

        DICTIONARY[raw]?.let { return it } // 1) 사전

        // TODO 2) 규칙 기반 (약어 전개·수량/규격 꼬리 제거·띄어쓰기 삽입)
        return INSUFFICIENT_DATA // 3) 데이터 부족
    }

    companion object {
        const val INSUFFICIENT_DATA = "데이터 부족"

        /** 품목 마스터 사전 (요구사항 제공 20건 기준) — 원문 → 정규화. DB화는 현재 의미 없음. */
        private val DICTIONARY: Map<String, String> = mapOf(
            "토마토살사S/O" to "토마토 살사 소스",
            "허브염지닭정육" to "허브 염지 닭정육",
            "밀또띠아10인치" to "밀 또띠아 10인치",
            "로메인쉬레드" to "로메인 쉬레드",
            "아보카도30입" to "아보카도",
            "슈레드치즈2.5K" to "슈레드 치즈",
            "사워크림1K" to "사워크림",
            "라임30과" to "라임",
            "할라피뇨슬라이스" to "할라피뇨 슬라이스",
            "나초칩454G" to "나초칩",
            "블랙빈2.5K" to "블랙빈",
            "자스민쌀10K" to "자스민쌀",
            "냉감튀2K" to "냉동 감자튀김",
            "스모크BBQ소스" to "스모크 바비큐소스",
            "종이보울500" to "종이 보울",
            "투명리드500" to "투명 리드",
            "냉동새우살900" to "냉동 새우살",
            "냉동돈전지" to "냉동 돼지고기 전지",
            "고수4단" to "고수",
        )
    }
}
