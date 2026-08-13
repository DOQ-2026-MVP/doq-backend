package com.doq.comfozi.ingestion.extraction

/**
 * 추출기가 없을 때 쓰는 대체 — 문서 텍스트를 **통째로 한 항목의 품목명 자리에** 담는다.
 *
 * 없는 값을 지어내는 대신, 읽어낸 원문을 그대로 보여주고 나머지 필드는 비운다. 그러면 필수값 누락
 * 으로 검수 인박스에 올라오고 사람이 화면에서 보고 채울 수 있다 — 파일만 덩그러니 남아 아무 데도
 * 나타나지 않는 것보다 낫다.
 *
 * 빈(bean)이 아니라 object 인 이유: [AnthropicItemExtractor] 와 함께 후보가 되면 주입이 모호해진다.
 * 호출부가 "추출기가 있으면 그것, 없으면 이것"으로 고른다.
 */
object RawTextItemExtractor : ItemExtractor {

    override fun extract(fileName: String, text: String): List<ExtractedItem> =
        listOf(ExtractedItem(rawItemName = text))
}
