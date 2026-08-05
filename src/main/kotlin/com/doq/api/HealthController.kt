package com.doq.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/ping")
    fun ping(): Map<String, Any> = mapOf(
        "status" to "ok",
        "timestamp" to Instant.now().toString(),
    )
}
