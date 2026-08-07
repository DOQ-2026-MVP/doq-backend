package com.doq.comfozi.structuring.ingestion.support

/**
 * 취합 파일(BATCH_FILE) 컬럼 스키마 (요구사항 §입력 파일 컬럼 9개).
 *
 * 헤더 라벨과 정규화 규칙을 **한 곳에서** 정의한다:
 * - [IngestionUploadBatchFileParser]는 [entries]로 필수 헤더 존재 검증에,
 * - structuring의 `BatchFileRecordMapper`는 [normalized] 키로 값 룩업에 공유한다.
 *
 * 이 enum은 캐노니컬 필드(MappedRecord)를 모른다 — 헤더 → 필드 대입은 매퍼가 명시적으로 한다.
 */
enum class BatchFileColumn(val label: String) {
    DOC_ID("문서ID"),
    SOURCE_TYPE("원본유형"),
    SUPPLIER("공급사"),
    RAW_ITEM_NAME("원문품목명"),
    SPEC("규격"),
    UNIT("단위"),
    PRICE_BEFORE("기존단가(원)"),
    PRICE_AFTER("변경단가(원)"),
    EFFECTIVE_DATE("적용일"),
    ;

    /** 매칭·룩업용 정규화 헤더 키 (생성 시 1회 계산). */
    val normalized: String = normalizeHeader(label)

    companion object {
        fun normalize(header: String): String = normalizeHeader(header)
    }
}

/**
 * 헤더 정규화 — 소문자 · 공백/"(원)" 제거. (예: "기존단가(원)"→"기존단가", "원문 품목명"→"원문품목명")
 */
private fun normalizeHeader(header: String): String {
    return header.trim()
        .lowercase()
        .replace(" ", "")
        .replace("(원)", "")
}
