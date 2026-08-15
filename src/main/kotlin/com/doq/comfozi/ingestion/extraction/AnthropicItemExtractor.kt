package com.doq.comfozi.ingestion.extraction

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.doq.common.config.AppObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

/**
 * Claude 기반 항목 추출기 — PDF 에서 뽑은 **텍스트**를 넘겨 항목 목록을 받는다.
 *
 * PDF 를 통째로 실어 보내지 않는 이유: 제공 문서는 텍스트 레이어가 온전해 텍스트만으로 충분하고,
 * 토큰·지연이 크게 준다. 대신 표 정렬이 평문으로 무너지므로 프롬프트가 열 대응을 설명한다.
 *
 * 기본 모델이 작은 축인 것도 같은 이유다 — 짧은 텍스트에서 정해진 7필드를 뽑는 구조화 작업이라
 * 큰 모델이 필요하지 않다. 추출 품질이 아쉬우면 `ANTHROPIC_MODEL` 로 올리면 된다.
 *
 * [AnthropicClient] 빈이 있을 때만(= `ANTHROPIC_API_KEY` 설정 시) 활성화된다.
 */
@Component
@ConditionalOnBean(AnthropicClient::class)
class AnthropicItemExtractor(
    private val client: AnthropicClient,
    @Value("\${anthropic.model:claude-haiku-4-5-20251001}") private val model: String,
    @Value("\${anthropic.max-tokens:8000}") private val maxTokens: Long,
) : ItemExtractor {

    override fun extract(fileName: String, text: String): List<ExtractedItem> {
        val response = client.messages().create(
            MessageCreateParams.builder()
                .model(Model.of(model))
                .maxTokens(maxTokens)
                .addUserMessage("$PROMPT\n\n--- 문서: $fileName ---\n$text")
                .build(),
        )

        val answer = response.content()
            .flatMap { block -> block.text().map { it.text() }.orElse("").let(::listOf) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        return parse(answer)
    }

    /** 응답에서 JSON 만 뽑는다 — 코드펜스나 설명이 섞여 와도 첫 `{` ~ 마지막 `}` 만 취한다. */
    private fun parse(answer: String): List<ExtractedItem> {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        require(start in 0 until end) { "추출 응답에서 JSON 을 찾지 못했습니다" }

        val json = answer.substring(start, end + 1)
        return AppObjectMapper.instance.readValue(json, ExtractionResult::class.java).items
    }

    /** 응답 래퍼 — `{ "items": [ {7필드} ] }`. */
    private data class ExtractionResult(val items: List<ExtractedItem> = emptyList())

    private companion object {
        val PROMPT = """
            아래는 구매 증빙 공문에서 추출한 텍스트입니다. 문서에 담긴 **단가 변경 항목**을 모두 찾아
            JSON 으로만 답하세요.

            각 항목은 아래 7개 필드를 가집니다(영문 키). 값은 문서에 적힌 **원문 그대로** 문자열로 담고
            숫자·날짜도 변환하지 마세요. 문서에 없으면 null 이며, 없는 항목을 지어내지 마세요.

            - supplier: 공급사 (문서 발신 회사)
            - rawItemName: 품목명 (표기 그대로)
            - spec: 규격
            - unit: 단위
            - priceBefore: 기존단가
            - priceAfter: 변경단가
            - effectiveDate: 적용일

            주의:
            - 표가 평문으로 펼쳐져 있을 수 있습니다. 머리글 순서를 보고 각 값이 어느 열인지 판단하세요.
              (문서마다 열 구성이 다릅니다 — 예: `품목 규격 단위 기존단가 변경단가 적용일자`,
               `번호 품목명 규격 단위 기존단가 변경단가 인상률`)
            - "추후 안내" 처럼 숫자가 아닌 값도 **그대로** 담으세요. 비우지 마세요.
            - 표에 없고 문서 머리·발신부에만 한 번 나오는 값은 **모든 항목에 같은 값**으로 채우세요:
              공급사(발신 회사), 그리고 적용일자 열이 없으면 적용일(`시행일`·`적용일` 등).
            - 회사명이 `가 온 푸 드` 처럼 자간이 벌어져 있으면 공백을 붙여 `가온푸드` 로 적으세요.
            - 문서번호·문서ID·원본유형은 뽑지 마세요. 시스템이 따로 부여합니다.

            형식(다른 텍스트·설명·코드펜스 없이 이 JSON 만):
            {"items":[{"supplier":"...","rawItemName":"...","spec":"...","unit":"...","priceBefore":"...","priceAfter":"...","effectiveDate":"..."}]}
        """.trimIndent()
    }
}
