package com.doq.comfozi.structuring

import com.doq.comfozi.structuring.ingestion.service.IngestionReadService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StructuringServiceImpl(
    private val readService: IngestionReadService,
    private val eventPublisher: ApplicationEventPublisher,
) : StructuringService {

    @Transactional
    override fun struct(ingestionId: Long) {
        readService.getSession(ingestionId) // 존재 확인 (없으면 예외)
        val records = readService.getRecords(ingestionId)

        // TODO 매핑(raw→캐노니컬) · 정규화(품목명 사전→규칙→"데이터 부족")
        // TODO detection: 필수값 누락·규격/단위 불일치(레코드 단위) + 중복 의심(세션 집합 비교)
        records.forEach { record ->
            eventPublisher.publishEvent(
                RecordStructured(
                    ingestionId = ingestionId,
                    recordId = requireNotNull(record.id),
                    uploadType = record.uploadRef?.uploadType,
                    rowNo = record.uploadRef?.rowNo,
                    observed = record.content.values, // TODO: 매핑/정규화 결과로 교체 (현재 raw)
                    flags = emptySet(), // TODO: detection 결과로 교체
                ),
            )
        }
    }
}
