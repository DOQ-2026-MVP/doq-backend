package com.doq.comfozi.structuring.ingestion

/**
 * 취합 파일 파싱 결과 → 원본 행 엔티티들. 헤더명으로 컬럼을 매칭하고 값은 원문 그대로 담는다.
 * 각 행은 파일 출처이므로 [IngestionRecord.uploadRef]를 채운다(BATCH_FILE, 행번호).
 */
fun IngestionUploadBatchFileContent.toEntities(ingestionId: Long, uploadId: Long): List<IngestionRecord> {
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

/**
 * 수기 입력 → 원본 행 엔티티. 업로드 출처가 없으므로 [IngestionRecord.uploadRef]는 null.
 * 값은 원문 그대로(검증/정규화는 후속 structuring).
 */
fun IngestionManualInput.toEntity(ingestionId: Long): IngestionRecord =
    IngestionRecord(
        ingestionId = ingestionId,
        uploadRef = null,
        docId = docId,
        sourceType = sourceType,
        supplier = supplier,
        rawItemName = rawItemName,
        spec = spec,
        unit = unit,
        priceBefore = priceBefore,
        priceAfter = priceAfter,
        effectiveDate = effectiveDate,
    )
