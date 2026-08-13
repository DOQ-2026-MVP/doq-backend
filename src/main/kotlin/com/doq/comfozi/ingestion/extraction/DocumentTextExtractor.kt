package com.doq.comfozi.ingestion.extraction

/**
 * 업로드 원본 → 텍스트. 형식별 구현이 "글자를 꺼내는 법"만 감추고, 그 뒤(항목 해석·적재·검수)는
 * 모두 같은 길을 탄다 — PDF 는 박혀 있는 텍스트 레이어를, 이미지는 OCR 을 쓴다.
 *
 * 텍스트만 만들면 [ItemExtractor] 구현(규칙 파서·LLM)이 그대로 붙는 것이 이 포트의 요점이다.
 */
fun interface DocumentTextExtractor {

    /** 원본 바이트에서 글자를 꺼낸다. 읽어낼 글자가 없으면 [IllegalArgumentException]. */
    fun extract(bytes: ByteArray): String
}
