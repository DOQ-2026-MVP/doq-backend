package com.doq.common.config

import org.hibernate.cfg.AvailableSettings
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Hibernate의 jsonb (역)직렬화가 전역 [AppObjectMapper]를 쓰게 한다.
 *
 * KotlinModule(데이터클래스 생성자)·JavaTime이 있어야 `MappedRecord` 같은 코틀린 타입을 jsonb로
 * 저장/복원할 수 있다(기본 Hibernate 매퍼는 KotlinModule이 없어 역직렬화 실패).
 */
@Configuration
class HibernateJsonConfig {

    @Bean
    fun jsonFormatMapperCustomizer(): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { props ->
            props[AvailableSettings.JSON_FORMAT_MAPPER] = JacksonJsonFormatMapper(AppObjectMapper.instance)
        }
}
