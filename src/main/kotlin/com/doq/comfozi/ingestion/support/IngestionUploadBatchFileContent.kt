package com.doq.comfozi.ingestion.support

import com.doq.comfozi.ingestion.domain.IngestionContent
import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.ingestion.domain.IngestionUploadRef
import com.doq.comfozi.ingestion.domain.IngestionUploadType

/** 파싱 결과 — 헤더 1줄 + 데이터 행들(각 행은 셀 문자열 리스트). CSV·XLSX 공통. */
data class IngestionUploadBatchFileContent(val header: List<String>, val rows: List<List<String>>) {

    /**
     * 원본 행 엔티티들로 변환 — 헤더→셀 값을 [IngestionContent]에 **원문 그대로** 담는다(매핑 없음).
     * 헤더→구조화 필드 매핑은 인입이 아니라 후속 structuring에서 수행한다.
     * 각 행은 파일 출처이므로 [IngestionRecord.uploadRef]를 채운다(BATCH_FILE, 행번호).
     */
    fun toEntities(ingestionId: Long, uploadId: Long): List<IngestionRecord> =
        rows.mapIndexed { i, row ->
            val values = header.mapIndexed { idx, h -> h to row.getOrNull(idx) }.toMap()
            IngestionRecord(
                ingestionId = ingestionId,
                uploadRef = IngestionUploadRef(
                    uploadId = uploadId,
                    uploadType = IngestionUploadType.BATCH_FILE,
                    rowNo = i + 2, // 파일 행번호 (헤더 = 1행)
                ),
                content = IngestionContent(values),
            )
        }
}
