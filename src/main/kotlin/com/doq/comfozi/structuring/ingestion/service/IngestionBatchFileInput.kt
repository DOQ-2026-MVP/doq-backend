package com.doq.comfozi.structuring.ingestion.service

import java.io.InputStream

/**
 * 취합 파일(BATCH_FILE) 업로드 입력 — 파일명·content-type·원본 스트림 묶음.
 *
 * 스트림을 담으므로 값 객체가 아니라 일반 클래스다(equals/copy 부적절).
 */
class IngestionBatchFileInput(
    val fileName: String,
    val contentType: String?,
    val content: InputStream,
)
