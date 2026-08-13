package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.ingestion.domain.IngestionUploadType
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/**
 * 수기 입력 매퍼 — content가 이미 캐노니컬 키(`docId·…`)로 저장돼 있어, 키명이 곧 [MappedRecord] 프로퍼티명이라
 * ObjectMapper로 그대로 역직렬화한다(키 하드코딩 없음).
 * 캐노니컬 키는 [com.doq.comfozi.ingestion.service.IngestionManualInput] 직렬화와 짝을 이룬다.
 */
@Component
class ManualRecordMapper(
    private val objectMapper: ObjectMapper,
) : RecordMapper {

    override fun supports(uploadType: IngestionUploadType?): Boolean = uploadType == null

    override fun map(record: IngestionRecord): MappedRecord =
        objectMapper.convertValue(record.content.values, MappedRecord::class.java)
}
