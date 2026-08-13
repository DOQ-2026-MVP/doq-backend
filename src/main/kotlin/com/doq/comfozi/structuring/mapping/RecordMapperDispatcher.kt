package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.ingestion.domain.IngestionRecord
import org.springframework.stereotype.Component

/**
 * 매핑 디스패처 — 레코드 출처([IngestionRecord.uploadRef])에 맞는 [RecordMapper]를 골라 위임한다.
 *
 * 지원하는 매퍼가 없으면 예외. (FILE·OCR 등 미정형 출처는 전용 매퍼가 생기면 자동 편입된다.)
 */
@Component
class RecordMapperDispatcher(private val mappers: List<RecordMapper>) {

    fun map(record: IngestionRecord): MappedRecord {
        val uploadType = record.uploadRef?.uploadType
        val mapper = mappers.firstOrNull { it.supports(uploadType) }
            ?: error("지원하는 매퍼가 없습니다: uploadType=$uploadType")
        return mapper.map(record)
    }
}
