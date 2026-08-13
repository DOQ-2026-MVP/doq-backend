package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.ingestion.domain.IngestionUploadType
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/**
 * 원본 문서(FILE) 매퍼 — PDF 등에서 추출한 항목. 추출 단계가 이미 캐노니컬 키(`docId·…`)로 content 에
 * 담아 두므로 [ManualRecordMapper] 와 동일하게 ObjectMapper 로 그대로 역직렬화한다.
 *
 * 추출 규칙은 [com.doq.comfozi.ingestion.extraction.ItemExtractor] 소관이고, 여기서는 옮기기만 한다.
 */
@Component
class FileRecordMapper(
    private val objectMapper: ObjectMapper,
) : RecordMapper {

    override fun supports(uploadType: IngestionUploadType?): Boolean = uploadType == IngestionUploadType.FILE

    override fun map(record: IngestionRecord): MappedRecord =
        objectMapper.convertValue(record.content.values, MappedRecord::class.java)
}
