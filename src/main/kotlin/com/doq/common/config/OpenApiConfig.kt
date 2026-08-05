package com.doq.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger / OpenAPI 문서 설정.
 *
 * springdoc가 컨트롤러를 스캔해 스펙을 만든다. 이 빈은 문서의 메타(제목·설명·버전)만 정의.
 *   - Swagger UI : `/swagger-ui.html`
 *   - OpenAPI JSON: `/v3/api-docs`
 *
 * 공통 응답 스키마([com.doq.common.web.ApiResponse])는 각 컨트롤러가
 * 반환 타입으로 노출하면 springdoc가 자동으로 스펙에 포함한다.
 */
@Configuration
class OpenApiConfig(
    @param:Value("\${spring.application.name:doq}") private val appName: String,
) {
    @Bean
    fun doqOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("doq API")
                .description("doq 구조화 백엔드 API 문서 — 공통 응답 envelope는 `ApiResponse` 스키마 참고")
                .version("v0.0.1"),
        )
}
