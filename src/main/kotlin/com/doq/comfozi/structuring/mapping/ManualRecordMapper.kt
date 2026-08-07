package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import org.springframework.stereotype.Component

/**
 * 수기 입력 매퍼 — content가 이미 캐노니컬 키(`docId·…`)로 저장돼 있어 그대로 읽는다.
 * (키 스키마는 [com.doq.comfozi.structuring.ingestion.service.IngestionManualInput]와 짝을 이룬다.)
 */
@Component
class ManualRecordMapper : RecordMapper {

    override fun supports(uploadType: IngestionUploadType?): Boolean = uploadType == null

    override fun map(record: IngestionRecord): MappedRecord {
        val v = record.content.values
        return MappedRecord(
            docId = v["docId"],
            sourceType = v["sourceType"],
            supplier = v["supplier"],
            rawItemName = v["rawItemName"],
            spec = v["spec"],
            unit = v["unit"],
            priceBefore = v["priceBefore"],
            priceAfter = v["priceAfter"],
            effectiveDate = v["effectiveDate"],
        )
    }
}
