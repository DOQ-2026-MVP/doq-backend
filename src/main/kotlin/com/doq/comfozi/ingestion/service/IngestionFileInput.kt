package com.doq.comfozi.ingestion.service

import java.io.InputStream

/**
 * 파일 업로드 입력 — 파일명·content-type·원본 스트림 묶음.
 *
 * 취합 표 파일이든 원본 증빙 문서든 **입구는 하나**다. 어느 쪽인지는 서비스가 내용(매직 바이트)으로
 * 판정하므로 호출부가 미리 구분하지 않는다.
 *
 * 스트림을 담으므로 값 객체가 아니라 일반 클래스다(equals/copy 부적절).
 */
class IngestionFileInput(
    val fileName: String,
    val contentType: String?,
    val content: InputStream,
)
