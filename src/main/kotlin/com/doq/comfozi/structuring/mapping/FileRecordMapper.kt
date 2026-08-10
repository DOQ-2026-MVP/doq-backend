package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/**
 * FILE 매퍼 — 원본 문서 파일(PDF·이미지 등)에서 추출한 항목. 추출기가 이미 캐노니컬 키(`docId·…`)로
 * content에 담아두므로 [ManualRecordMapper]와 동일하게 ObjectMapper로 그대로 역직렬화한다.
 * (추출 규칙은 [com.doq.comfozi.structuring.ingestion.pdf.PdfRecordExtractor] 소관.)
 */
@Component
class FileRecordMapper(
    private val objectMapper: ObjectMapper,
) : RecordMapper {

    override fun supports(uploadType: IngestionUploadType?): Boolean = uploadType == IngestionUploadType.FILE

    override fun map(record: IngestionRecord): MappedRecord =
        objectMapper.convertValue(record.content.values, MappedRecord::class.java)
}
