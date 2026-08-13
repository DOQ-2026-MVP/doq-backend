package com.doq.comfozi.structuring.ingestion.service

import java.io.InputStream

/**
 * 원본 문서(FILE) 업로드 입력 — PDF·이미지 증빙 원본. 파일명·content-type·원본 스트림 묶음.
 *
 * [IngestionBatchFileInput]과 모양은 같지만 처리가 다르다 — 이쪽은 **보관만** 하고 행을 만들지 않는다.
 * 스트림을 담으므로 값 객체가 아니라 일반 클래스다(equals/copy 부적절).
 */
class IngestionDocumentInput(
    val fileName: String,
    val contentType: String?,
    val content: InputStream,
)
