package com.doq.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

/** `@Async`·`@Scheduled` 활성화 + 인입 파이프라인 전용 스레드풀. */
@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfig {

    /**
     * 취합 파일 파싱 워커 풀 — 요청 스레드와 분리해 업로드 응답이 파싱을 기다리지 않게 한다.
     *
     * - **CallerRuns** 로 포화 처리: 큐가 차면 거절(AbortPolicy) 대신 호출 스레드에서 돌린다.
     *   거절되면 업로드가 PARSING 에 영영 갇히므로, 느려지더라도 처리되는 쪽이 낫다.
     * - **종료 시 대기**: 진행 중인 파싱을 끝내고 내려간다(마찬가지로 PARSING 잔류 방지).
     */
    @Bean(INGESTION_PARSE_EXECUTOR)
    fun ingestionParseExecutor(
        @Value("\${app.ingestion.parse.core-pool-size:2}") corePoolSize: Int,
        @Value("\${app.ingestion.parse.max-pool-size:4}") maxPoolSize: Int,
        @Value("\${app.ingestion.parse.queue-capacity:100}") queueCapacity: Int,
    ): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        this.corePoolSize = corePoolSize
        this.maxPoolSize = maxPoolSize
        setQueueCapacity(queueCapacity)
        setThreadNamePrefix("ingestion-parse-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS)
    }

    /**
     * 현황 스트림(SSE) 전송 스레드 — **1개**로 고정한다. 이벤트가 발행 순서대로 나가야
     * 구독자가 뒤늦은 옛 현황으로 화면을 덮어쓰지 않는다. 전송을 요청 스레드에서 떼어내는 것이
     * 목적이므로 처리량은 중요하지 않다.
     */
    @Bean(INGESTION_EVENT_EXECUTOR)
    fun ingestionEventExecutor(
        @Value("\${app.ingestion.events.queue-capacity:1000}") queueCapacity: Int,
    ): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 1
        maxPoolSize = 1
        setQueueCapacity(queueCapacity)
        setThreadNamePrefix("ingestion-event-")
        // 큐가 넘치면 호출 스레드에서 보낸다 — 이벤트를 버리느니 잠깐 느려지는 편이 낫다
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS)
    }

    companion object {
        /** 인입 파싱 워커 풀 빈 이름 — `@Async` 에서 지정한다. */
        const val INGESTION_PARSE_EXECUTOR = "ingestionParseExecutor"

        /** 현황 스트림 전송 스레드 빈 이름 — `@Async` 에서 지정한다. */
        const val INGESTION_EVENT_EXECUTOR = "ingestionEventExecutor"

        private const val AWAIT_TERMINATION_SECONDS = 30
    }
}
