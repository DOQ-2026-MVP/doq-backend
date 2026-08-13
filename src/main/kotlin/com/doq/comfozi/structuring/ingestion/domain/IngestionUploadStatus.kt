package com.doq.comfozi.structuring.ingestion.domain

/**
 * 업로드 처리 현황 — [IngestionUpload] 한 건이 지금 어디까지 처리됐는가.
 *
 * 파싱 실패한 취합 파일은 애초에 저장되지 않으므로(parse-before-persist) 실패 상태는 없다.
 * PDF·이미지 원본 문서는 보관만 하고 행 추출은 아직 지원하지 않아 [PENDING_EXTRACTION]에 머문다.
 */
enum class IngestionUploadStatus {
    /** 취합 파일(BATCH_FILE) 파싱 완료 — 원본 행이 적재됐다. */
    PARSED,

    /** 원본 문서(FILE) 보관 완료 — 행 추출은 미지원이라 수기 입력으로 보완한다. */
    PENDING_EXTRACTION,
}
