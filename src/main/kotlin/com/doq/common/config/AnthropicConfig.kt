package com.doq.common.config

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Anthropic 클라이언트 — 원본 문서(PDF)에서 증빙 항목을 뽑는 데 쓴다(추가 요건).
 *
 * `anthropic.api-key`(= 환경변수 `ANTHROPIC_API_KEY`, relaxed binding)가 있을 때만 빈이 생성되고,
 * 그에 따라 추출기([com.doq.comfozi.ingestion.extraction.AnthropicItemExtractor])도 함께 켜진다.
 * 키가 없어도 부팅·업로드는 정상이며 PDF 는 보관만 된다(행 0건).
 *
 * application.yml 에 `api-key` 기본값을 두지 않는 이유: 빈 값이라도 "존재"로 잡혀 오히려 켜진다.
 */
@Configuration
class AnthropicConfig {

    @Bean
    @ConditionalOnProperty(prefix = "anthropic", name = ["api-key"])
    fun anthropicClient(): AnthropicClient =
        AnthropicOkHttpClient.fromEnv() // ANTHROPIC_API_KEY 를 읽는다
}
