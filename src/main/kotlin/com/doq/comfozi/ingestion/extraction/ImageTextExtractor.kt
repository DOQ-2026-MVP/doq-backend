package com.doq.comfozi.ingestion.extraction

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 이미지 원본 → 텍스트 (Tesseract OCR). 촬영·스캔된 공문에서 글자를 꺼낸다.
 *
 * 외부 실행 파일을 부르는 이유: JVM 에 쓸 만한 순수 OCR 이 없고, 네이티브 바인딩(JNA)보다
 * 프로세스 호출이 배포·장애 처리 모두 단순하다. 설치돼 있지 않으면 [isAvailable] 이 false 이고
 * 이미지는 행 없이 완료 처리된다 — 설치를 안 한 것은 파일 문제가 아니라서 실패로 보지 않는다.
 *
 * **`--psm 6`(단일 텍스트 블록)이 중요하다.** 기본값(3)으로는 표가 통째로 무너져 한 행도 못 읽는다.
 * 6 에서는 표 한 행이 한 줄로 나와 [TableTextItemExtractor] 가 그대로 읽는다.
 *
 * 제공된 공문 2장 기준으로 기울기(2~4°)·어두운 배경은 tesseract 가 알아서 처리해 별도 전처리
 * (deskew·crop)를 두지 않았다.
 */
@Component
class ImageTextExtractor(
    @Value("\${app.ingestion.ocr.command:tesseract}") private val command: String,
    @Value("\${app.ingestion.ocr.languages:kor+eng}") private val languages: String,
    @Value("\${app.ingestion.ocr.timeout-seconds:60}") private val timeoutSeconds: Long,
) : DocumentTextExtractor {

    private val log = LoggerFactory.getLogger(javaClass)

    /** OCR 실행 파일이 있는가 — 기동 시 한 번만 확인한다. */
    val isAvailable: Boolean by lazy {
        runCatching { run(listOf(command, "--version"), input = null).isNotBlank() }
            .onFailure { log.info("OCR 실행 파일({})을 찾지 못했다 — 이미지는 보관만 된다", command) }
            .getOrDefault(false)
    }

    override fun extract(bytes: ByteArray): String {
        // stdin(`-`) 으로 넣고 stdout 으로 받는다 — 임시 파일을 남기지 않는다
        val raw = run(listOf(command, "-", "stdout", "--psm", "6", "-l", languages), input = bytes)
        val text = cleaned(raw)

        require(text.isNotBlank()) {
            "이미지에서 글자를 읽지 못했습니다 (해상도가 낮거나 문서가 아닌 이미지일 수 있습니다)"
        }
        return text
    }

    /**
     * 표 괘선이 글자로 섞여 들어온 것을 걷어낸다 — 세로선이 `|` 나 홀로 선 `=` 로 잡힌다.
     * 열 사이 간격은 그대로 둔다(그게 열 구분이다).
     */
    private fun cleaned(raw: String): String =
        raw.lineSequence()
            .map { it.replace('|', ' ').replace(RULE_ARTIFACT, " ").trimEnd() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    private fun run(command: List<String>, input: ByteArray?): String {
        val process = ProcessBuilder(command).redirectErrorStream(false).start()

        // 이미지를 먼저 다 밀어 넣는다 — OCR 은 입력을 전부 읽어야 출력을 내므로 교착되지 않는다
        process.outputStream.use { if (input != null) it.write(input) }
        val output = process.inputStream.bufferedReader().readText()

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalArgumentException("이미지 글자 인식이 ${timeoutSeconds}초 안에 끝나지 않았습니다")
        }
        require(process.exitValue() == 0) { "이미지 글자 인식에 실패했습니다" }
        return output
    }

    private companion object {
        /** 홀로 떨어진 `=`·`_` — 괘선이 글자로 읽힌 것. 값 안의 것은 공백에 둘러싸이지 않아 남는다. */
        val RULE_ARTIFACT = Regex("""(?<=\s)[=_]+(?=\s)""")
    }
}
