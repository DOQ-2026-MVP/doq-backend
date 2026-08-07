package com.doq.common.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * 전역 단일 [ObjectMapper] — DI(빈)와 **정적 참조**([AppObjectMapper.instance])가 같은 인스턴스를 공유한다.
 * (스프링이 관리하지 않는 곳 — 유틸·컨버터 등 — 에서도 동일 설정 매퍼를 쓰기 위함.)
 *
 * Spring Boot 자동 구성 매퍼를 대체하므로 잃기 쉬운 기본값을 명시한다:
 * - Kotlin 모듈(생성자·nullable·기본값) / JavaTime 모듈(`LocalDate` 등)
 * - 날짜는 ISO 문자열로 직렬화(타임스탬프 X)
 * - 모르는 필드는 무시(프론트가 추가 필드를 보내도 400 아님)
 */
object AppObjectMapper {
    val instance: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}

@Configuration
class JacksonConfig {

    /** MVC 메시지 변환 등 DI 경로용 — 정적 인스턴스와 동일 객체. */
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper = AppObjectMapper.instance
}
