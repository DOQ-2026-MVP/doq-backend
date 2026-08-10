package com.doq.common.config

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Anthropic 클라이언트 — PDF 원본 직접 추출(추가 요건)에서 Claude를 호출하는 데 쓴다.
 *
 * `anthropic.api-key`(= 환경변수 `ANTHROPIC_API_KEY`, relaxed binding)가 있을 때만 빈이 생성된다.
 * 키가 없으면 빈이 없고, PDF 추출기([com.doq.comfozi.structuring.ingestion.pdf.AnthropicPdfRecordExtractor])도
 * 함께 비활성화된다 — 그 상태에서 PDF 업로드를 요청하면 서비스가 "미구성" 오류를 낸다.
 */
@Configuration
class AnthropicConfig {

    @Bean
    @ConditionalOnProperty(prefix = "anthropic", name = ["api-key"])
    fun anthropicClient(): AnthropicClient =
        AnthropicOkHttpClient.fromEnv() // ANTHROPIC_API_KEY를 읽는다
}
