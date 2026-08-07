package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionUploadType

/**
 * 매핑 전략 — 출처별 원문을 캐노니컬 [MappedRecord]로 옮긴다.
 *
 * 출처마다 원문 형식이 달라(수기=캐노니컬 키, BATCH_FILE=파일 헤더, …) 구현을 나눈다.
 * 각 구현은 **정해진 형식**을 전제로 자기 스키마를 직접 매핑한다. 선택은 [RecordMapperDispatcher].
 */
interface RecordMapper {

    /** 이 매퍼가 담당하는 출처인지. null = 수기(uploadRef 없음). */
    fun supports(uploadType: IngestionUploadType?): Boolean

    fun map(record: IngestionRecord): MappedRecord
}
