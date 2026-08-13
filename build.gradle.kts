plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    kotlin("plugin.jpa") version "2.1.20"
    id("org.springframework.boot") version "3.5.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.doq"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web (Spring MVC)
    implementation("org.springframework.boot:spring-boot-starter-web")
    // JPA (PostgreSQL)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Flyway (schema migration for PostgreSQL)
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    // 취합 파일 파싱 — CSV(Apache Commons CSV) / XLSX(Apache POI)
    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    // 원본 문서(PDF) 텍스트 추출 — 제공 PDF는 텍스트 레이어가 온전해 OCR 없이 뽑힌다
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    // 추출된 텍스트 → 증빙 항목 (Claude). ANTHROPIC_API_KEY 있을 때만 활성화된다.
    implementation("com.anthropic:anthropic-java:2.34.0")
    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // OpenAPI / Swagger UI (springdoc, Spring MVC) — /swagger-ui.html, /v3/api-docs
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    // Docker Compose support (auto-launch compose.yaml on bootRun, dev only)
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // Kotlin support
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database drivers
    runtimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
