package com.doq.comfozi.structuring.ingestion.pdf

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.TextBlockParam
import com.doq.common.config.AppObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import java.util.Base64

/**
 * Claude(Anthropic) 기반 PDF 추출기 — PDF 원본을 base64 document 블록으로 실어 보내 항목 9필드를 뽑는다.
 *
 * [AnthropicClient] 빈이 있을 때만(=`ANTHROPIC_API_KEY` 설정 시) 활성화된다.
 * 값은 원문 그대로(문자열, 변환 없음) 받아오며 검증·정규화는 후속 structuring이 맡는다.
 */
@Component
@ConditionalOnBean(AnthropicClient::class)
class AnthropicPdfRecordExtractor(
    private val client: AnthropicClient,
    @Value("\${anthropic.model:claude-opus-4-8}") private val model: String,
    @Value("\${anthropic.max-tokens:8000}") private val maxTokens: Long,
) : PdfRecordExtractor {

    override fun extract(fileName: String, pdfBytes: ByteArray): List<PdfExtractedItem> {
        val base64 = Base64.getEncoder().encodeToString(pdfBytes) // 개행 없는 표준 base64
        val document = DocumentBlockParam.builder()
            .source(com.anthropic.models.messages.Base64PdfSource.builder().data(base64).build())
            .build()

        val response = client.messages().create(
            MessageCreateParams.builder()
                .model(Model.of(model))
                .maxTokens(maxTokens)
                .addUserMessageOfBlockParams(
                    listOf(
                        ContentBlockParam.ofDocument(document),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(PROMPT).build()),
                    ),
                )
                .build(),
        )

        val text = response.content().stream()
            .flatMap { it.text().stream() }
            .map { it.text() }
            .findFirst()
            .orElse("")

        return parse(text)
    }

    /** 응답 텍스트에서 JSON을 뽑아 항목 목록으로. 코드펜스가 섞여 와도 첫 `{`~마지막 `}`만 취한다. */
    private fun parse(text: String): List<PdfExtractedItem> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start in 0 until end) { "PDF 추출 응답에서 JSON을 찾지 못했습니다" }
        val json = text.substring(start, end + 1)
        val result = AppObjectMapper.instance.readValue(json, PdfExtractionResult::class.java)
        return result.items
    }

    /** LLM 응답 래퍼 — `{ "items": [ {9필드} ] }`. */
    private data class PdfExtractionResult(val items: List<PdfExtractedItem> = emptyList())

    private companion object {
        val PROMPT = """
            첨부한 PDF는 구매 증빙 문서입니다. 문서에 담긴 **구매 증빙 항목**을 모두 추출해 JSON으로만 답하세요.

            각 항목은 아래 9개 필드를 가집니다(캐노니컬 영문 키). 값은 문서에 적힌 **원문 그대로** 문자열로 담고,
            숫자·날짜도 변환하지 마세요. 값이 없으면 null. 문서에 없는 항목을 지어내지 마세요.

            - docId: 문서/항목 식별자
            - sourceType: 원본유형 (예: PDF)
            - supplier: 공급사
            - rawItemName: 원문 품목명
            - spec: 규격
            - unit: 단위
            - priceBefore: 기존단가(숫자 문자열, 콤마 제거 불필요)
            - priceAfter: 변경단가
            - effectiveDate: 적용일

            형식(다른 텍스트·설명·코드펜스 없이 이 JSON만):
            {"items":[{"docId":"...","sourceType":"...","supplier":"...","rawItemName":"...","spec":"...","unit":"...","priceBefore":"...","priceAfter":"...","effectiveDate":"..."}]}
        """.trimIndent()
    }
}
