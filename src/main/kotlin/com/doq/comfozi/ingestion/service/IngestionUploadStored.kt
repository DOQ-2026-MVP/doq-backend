package com.doq.comfozi.ingestion.service

import com.doq.comfozi.ingestion.support.ClassifiedFile

/**
 * 업로드 원본 보관 완료 — **커밋된 뒤** 후속 처리를 시작하라는 신호.
 *
 * 업로드 트랜잭션 안에서 발행되고 [IngestionUploadStoredListener]가 커밋 이후에 받는다.
 * 처리 경로가 있든([ClassifiedFile.BatchFile] 파싱) 아직 없든([ClassifiedFile.Document] 추출 미지원)
 * **모든 업로드가 발행**한다 — 후속 처리가 붙는 자리를 한 곳으로 두기 위해서다.
 *
 * 원본 바이트는 싣지 않는다 — 이미 저장돼 있으므로 워커가 storageKey 로 다시 읽는다.
 */
data class IngestionUploadStored(
    val uploadId: Long,
    val classified: ClassifiedFile,
)
