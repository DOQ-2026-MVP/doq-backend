package com.doq.comfozi.structuring.ingestion.service

import java.io.InputStream

/**
 * PDF 원본 업로드 입력 — 파일명·content-type·원본 스트림 묶음 (추가 요건, uploadType=FILE).
 *
 * 스트림을 담으므로 값 객체가 아니라 일반 클래스다(equals/copy 부적절). [IngestionBatchFileInput]와 형태가 같지만
 * 처리 경로(LLM 추출 vs 표 파싱)가 달라 타입을 분리한다.
 */
class IngestionPdfInput(
    val fileName: String,
    val contentType: String?,
    val content: InputStream,
)
