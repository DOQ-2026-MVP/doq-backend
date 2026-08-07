package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.doq.comfozi.structuring.ingestion.support.BatchFileColumn
import org.springframework.stereotype.Component

/**
 * BATCH_FILE 매퍼 — 요구사항 제공 취합 표 파일의 **정해진 헤더**를 캐노니컬 필드로 옮긴다.
 *
 * 헤더 어휘·정규화 규칙은 [BatchFileColumn](ingestion 소유)을 공유하고(파서의 검증과 동일 규칙),
 * 헤더 → 필드 대입만 여기서 명시한다. 표기 흔들림(대소문자·공백·"(원)")은 정규화로 흡수, 미정의 컬럼은 버린다.
 */
@Component
class BatchFileRecordMapper : RecordMapper {

    override fun supports(uploadType: IngestionUploadType?): Boolean = uploadType == IngestionUploadType.BATCH_FILE

    override fun map(record: IngestionRecord): MappedRecord {
        val v = record.content.values.mapKeys { BatchFileColumn.normalize(it.key) }
        return MappedRecord(
            docId = v[BatchFileColumn.DOC_ID.normalized],
            sourceType = v[BatchFileColumn.SOURCE_TYPE.normalized],
            supplier = v[BatchFileColumn.SUPPLIER.normalized],
            rawItemName = v[BatchFileColumn.RAW_ITEM_NAME.normalized],
            spec = v[BatchFileColumn.SPEC.normalized],
            unit = v[BatchFileColumn.UNIT.normalized],
            priceBefore = v[BatchFileColumn.PRICE_BEFORE.normalized],
            priceAfter = v[BatchFileColumn.PRICE_AFTER.normalized],
            effectiveDate = v[BatchFileColumn.EFFECTIVE_DATE.normalized],
        )
    }
}
