package com.doq.comfozi.structuring

import com.doq.comfozi.structuring.detection.AnomalyDetector
import com.doq.comfozi.structuring.detection.AnomalyRuleBasedFlag
import com.doq.comfozi.structuring.ingestion.domain.IngestionRecord
import com.doq.comfozi.structuring.ingestion.domain.IngestionStatus
import com.doq.comfozi.structuring.ingestion.service.IngestionReadService
import com.doq.comfozi.structuring.ingestion.service.IngestionService
import com.doq.comfozi.structuring.mapping.MappedRecord
import com.doq.comfozi.structuring.mapping.RecordMapperDispatcher
import com.doq.comfozi.structuring.normalization.ItemNameNormalizer
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StructuringServiceImpl(
    private val readService: IngestionReadService,
    private val ingestionService: IngestionService,
    private val recordMapperDispatcher: RecordMapperDispatcher,
    private val itemNameNormalizer: ItemNameNormalizer,
    private val anomalyDetector: AnomalyDetector,
    private val eventPublisher: ApplicationEventPublisher,
) : StructuringService {

    /**
     * DRAFT/FAILED 세션을 구조화한다(없으면 404, 이미 STRUCTURED거나 파싱 중이면 409, 비었으면 400).
     * 성공하면 같은 트랜잭션에서 STRUCTURED로, 실패하면 [IngestionService.markFailed]로 FAILED(별도 tx라
     * 작업 롤백돼도 남음) 후 예외 전파 → 재시도 가능.
     */
    @Transactional
    override fun struct(ingestionId: Long) {
        val session = readService.getSession(ingestionId) // 없으면 404 (관리 엔티티)

        check(session.status != IngestionStatus.STRUCTURED) {
            "이미 구조화된(STRUCTURED) 세션 (현재 ${session.status})"
        }
        // 처리가 비동기라 아직 행이 다 안 들어왔을 수 있다 — 반쪽짜리 세션을 구조화하지 않는다.
        // (PARSE_FAILED 는 더 들어올 행이 없으므로 막지 않는다 — 나머지 행으로 진행)
        check(readService.getUploads(ingestionId).all { it.status.isTerminal }) {
            "파싱이 끝나지 않은 업로드가 있어 구조화할 수 없음"
        }
        val records = readService.getRecords(ingestionId)
        require(records.isNotEmpty()) { "빈 세션은 구조화할 수 없음" }

        try {
            val observed = normalize(map(records))
            val flags = anomalyDetector.detect(observed)

            publish(ingestionId, records, observed, flags)
            session.markStructured() // DRAFT/FAILED → STRUCTURED (같은 tx, 더티체킹으로 영속)
        } catch (e: Exception) {
            ingestionService.markFailed(ingestionId) // REQUIRES_NEW — 롤백돼도 FAILED 커밋
            throw e
        }
    }

    /** 매핑 — 출처별 매퍼로 원문을 관찰값으로 옮긴다 (레코드 순서 유지). */
    private fun map(records: List<IngestionRecord>): List<MappedRecord> =
        records.map { recordMapperDispatcher.map(it) }

    /** 정규화 — 관찰값의 품목명을 채운다 (관찰값 그 자리에 세팅). */
    private fun normalize(observed: List<MappedRecord>): List<MappedRecord> =
        observed.onEach { it.normalizedItemName = itemNameNormalizer.normalize(it.rawItemName) }

    /** 발행 — 세션 구조화 완료본을 배치 이벤트 1개로 인계한다 (inspection이 Inspection+레코드로 영속). */
    private fun publish(
        ingestionId: Long,
        records: List<IngestionRecord>,
        observed: List<MappedRecord>,
        flags: List<Set<AnomalyRuleBasedFlag>>,
    ) {
        val items = records.mapIndexed { i, record ->
            StructuredRecord(
                recordId = requireNotNull(record.id),
                uploadType = record.uploadRef?.uploadType,
                rowNo = record.uploadRef?.rowNo,
                observed = observed[i],
                flags = flags[i],
            )
        }
        eventPublisher.publishEvent(StructuredRecords(ingestionId, items))
    }
}
