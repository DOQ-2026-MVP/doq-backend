package com.doq.comfozi.structuring.ingestion

import com.doq.comfozi.structuring.ingestion.service.IngestionManualInput
import java.time.LocalDate

/** 테스트용 완성 수기 입력 — 관심 필드만 override, 나머지는 유효 기본값. */
fun manualInput(
    docId: String = "MAN-1",
    rawItemName: String = "임시품목",
    sourceType: String = "수기",
    supplier: String = "직접입력",
    spec: String = "1kg/PK",
    unit: String = "PK",
    priceBefore: Long = 1000,
    priceAfter: Long = 1100,
    effectiveDate: LocalDate = LocalDate.of(2026, 8, 5),
) = IngestionManualInput(
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
