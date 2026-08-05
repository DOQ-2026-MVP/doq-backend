package com.doq.comfozi.structuring.ingestion.support

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadRef
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType

/** 파싱 결과 — 헤더 1줄 + 데이터 행들(각 행은 셀 문자열 리스트). CSV·XLSX 공통. */
data class IngestionUploadBatchFileContent(val header: List<String>, val rows: List<List<String>>) {

    /**
     * 헤더명 → 컬럼 인덱스 조회. 헤더 순서·표기 흔들림에 견디도록 정규화 후 매칭한다.
     * (예: "원문 품목명"→"원문품목명", "기존단가(원)"→"기존단가")
     */
    private class HeaderColumns(header: List<String>) {
        private val indexByKey: Map<String, Int> =
            header.withIndex().associate { (i, h) -> normalize(h) to i }

        /** 정규화된 [key]의 셀 값. 공백은 null. */
        fun value(row: List<String>, key: String): String? =
            indexByKey[key]?.let { row.getOrNull(it) }?.trim()?.ifBlank { null }

        companion object {
            fun normalize(header: String): String =
                header.trim().lowercase().replace(" ", "").replace("(원)", "")
        }
    }

    /**
     * 원본 행 엔티티들로 변환. 헤더명으로 컬럼을 매칭하고 값은 원문 그대로 담는다.
     * 각 행은 파일 출처이므로 [IngestionRecord.uploadRef]를 채운다(BATCH_FILE, 행번호).
     */
    fun toEntities(ingestionId: Long, uploadId: Long): List<IngestionRecord> {
        val col = HeaderColumns(header)
        return rows.mapIndexed { i, row ->
            IngestionRecord(
                ingestionId = ingestionId,
                uploadRef = IngestionUploadRef(
                    uploadId = uploadId,
                    uploadType = IngestionUploadType.BATCH_FILE,
                    rowNo = i + 2, // 파일 행번호 (헤더 = 1행)
                ),
                docId = col.value(row, "문서id"),
                sourceType = col.value(row, "원본유형"),
                supplier = col.value(row, "공급사"),
                rawItemName = col.value(row, "원문품목명"),
                spec = col.value(row, "규격"),
                unit = col.value(row, "단위"),
                priceBefore = col.value(row, "기존단가"),
                priceAfter = col.value(row, "변경단가"),
                effectiveDate = col.value(row, "적용일"),
            )
        }
    }
}
