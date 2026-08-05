package com.doq.comfozi.structuring.ingestion

import java.io.InputStream

/**
 * 파일 저장 포트 — 원본 바이트를 저장하고 이후 조회 가능한 [StoredFile.storageKey]를 돌려준다.
 * 현재 구현: 로컬 파일시스템([LocalFileStorage]). S3 등은 이 포트 뒤로 교체.
 */
interface FileStorage {

    /** 원본을 저장하고 저장 키·크기를 반환. */
    fun store(content: InputStream): StoredFile

    /** 저장 키로 원본을 다시 읽는다. */
    fun load(storageKey: String): InputStream
}

/** 저장 결과 — 파일시스템 경로 등에 의존하지 않는 불투명 키와 바이트 크기. */
data class StoredFile(
    val storageKey: String,
    val size: Long,
)
