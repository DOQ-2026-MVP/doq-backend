package com.doq.comfozi.structuring.mapping

import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.normalization.PriceNormalizer
import org.springframework.stereotype.Component

/**
 * 매핑 디스패처 — 레코드 출처([IngestionRecord.uploadRef])에 맞는 [RecordMapper]를 골라 위임한다.
 *
 * 지원하는 매퍼가 없으면 예외. (FILE·OCR 등 미정형 출처는 전용 매퍼가 생기면 자동 편입된다.)
 *
 * 출처를 가리지 않는 표기 정리는 매퍼가 아니라 여기서 한다 — 매퍼마다 흩어 두면 출처가 늘 때마다
 * 같은 손질을 다시 붙여야 하고, 한 곳만 빠뜨려도 그 출처의 값만 조용히 다른 모양으로 쌓인다.
 */
@Component
class RecordMapperDispatcher(
    private val mappers: List<RecordMapper>,
    private val priceNormalizer: PriceNormalizer,
) {

    fun map(record: IngestionRecord): MappedRecord {
        val uploadType = record.uploadRef?.uploadType
        val mapper = mappers.firstOrNull { it.supports(uploadType) }
            ?: error("지원하는 매퍼가 없습니다: uploadType=$uploadType")
        val mapped = mapper.map(record)

        return mapped.copy(
            priceBefore = priceNormalizer.normalize(mapped.priceBefore),
            priceAfter = priceNormalizer.normalize(mapped.priceAfter),
        )
    }
}
