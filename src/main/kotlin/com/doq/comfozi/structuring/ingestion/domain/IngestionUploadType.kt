package com.doq.comfozi.structuring.ingestion.domain

/**
 * 업로드 유형 — **파일 업로드** 경로 (per [IngestionUpload]).
 *
 * - [FILE]      : 원본 문서 파일 1개 (PDF·이미지·EML 등, 추가 요건). 0~1 record
 * - [BATCH_FILE] : 여러 원본유형을 한 파일로 취합한 표 파일(요구사항 제공 CSV/XLSX). 여러 행 → 여러 [IngestionRecord]
 *
 * 수기 입력은 업로드가 아니므로 여기 없음 — record만 만들고 [IngestionRecord.uploadRef]가 null.
 * 주의: [IngestionRecord] 데이터 컬럼 `sourceType`(PDF·XLSX·IMAGE·수기 = 증빙의 원래 유형)와 다르다.
 */
enum class IngestionUploadType {
    FILE,
    BATCH_FILE,
}
