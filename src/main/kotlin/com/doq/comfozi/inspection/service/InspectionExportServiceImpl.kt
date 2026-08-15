package com.doq.comfozi.inspection.service

import com.doq.comfozi.inspection.api.ExportChangeLogEntry
import com.doq.comfozi.inspection.api.ExportRow
import com.doq.comfozi.inspection.api.ExportSchema
import com.doq.comfozi.inspection.api.ExportSourceRef
import com.doq.comfozi.inspection.domain.InspectionChangeLog
import com.doq.comfozi.inspection.domain.InspectionChangeType
import com.doq.comfozi.inspection.domain.InspectionRecord
import com.doq.comfozi.inspection.domain.InspectionRecordStatus
import com.doq.comfozi.inspection.repository.InspectionChangeLogRepository
import com.doq.comfozi.inspection.repository.InspectionRecordRepository
import com.doq.comfozi.inspection.repository.InspectionRepository
import com.doq.comfozi.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class InspectionExportServiceImpl(
    private val inspectionRepository: InspectionRepository,
    private val recordRepository: InspectionRecordRepository,
    private val changeLogRepository: InspectionChangeLogRepository,
    private val ingestionRecordRepository: IngestionRecordRepository,
    private val ingestionUploadRepository: IngestionUploadRepository,
) : InspectionExportService {

    @Transactional(readOnly = true)
    override fun exportRows(inspectionId: Long): List<ExportRow> {
        if (!inspectionRepository.existsById(inspectionId)) {
            throw NoSuchElementException("알 수 없는 Inspection $inspectionId")
        }
        val approved = recordRepository.findByInspectionIdOrderByIdAsc(inspectionId)
            .filter { it.status == InspectionRecordStatus.CONFIRMED } // 승인 항목만
        if (approved.isEmpty()) return emptyList()

        // file_name 조인 — InspectionRecord.ingestionRecordId → IngestionRecord.uploadRef.uploadId → IngestionUpload.fileName
        val ingestionRecords = ingestionRecordRepository.findAllById(approved.map { it.ingestionRecordId })
            .associateBy { it.id }
        val uploadIds = ingestionRecords.values.mapNotNull { it.uploadRef?.uploadId }.distinct()
        val fileNameByUploadId = ingestionUploadRepository.findAllById(uploadIds)
            .associate { it.id to it.fileName }

        // change_log 임베드 — 승인 레코드들의 이력을 한 번에
        val logsByInspectionRecordId = changeLogRepository
            .findByInspectionRecordIdInOrderByIdAsc(approved.mapNotNull { it.id })
            .groupBy { it.inspectionRecordId }

        return approved.map { record ->
            val uploadId = ingestionRecords[record.ingestionRecordId]?.uploadRef?.uploadId
            toExportRow(
                record = record,
                fileName = uploadId?.let { fileNameByUploadId[it] },
                logs = logsByInspectionRecordId[record.id].orEmpty(),
            )
        }
    }

    private fun toExportRow(
        record: InspectionRecord,
        fileName: String?,
        logs: List<InspectionChangeLog>,
    ): ExportRow {
        val c = record.current
        return ExportRow(
            docId = c.docId,
            sourceType = c.sourceType,
            supplierName = c.supplier,
            rawItemName = c.rawItemName,
            normalizedItemName = c.normalizedItemName,
            spec = c.spec,
            unit = c.unit,
            priceBefore = ExportSchema.price(c.priceBefore),
            priceAfter = ExportSchema.price(c.priceAfter),
            effectiveDate = c.effectiveDate,
            reviewStatus = ExportSchema.reviewStatus(record.status),
            exceptionFlags = record.flags.sortedBy { it.ordinal }.map { ExportSchema.flag(it) },
            sourceRef = ExportSourceRef(
                inputMethod = if (record.uploadType == null) "manual" else "file",
                fileName = fileName,
                rowNo = record.rowNo,
            ),
            reviewedAt = logs.lastOrNull { it.type == InspectionChangeType.CONFIRM }?.createdAt?.kst(),
            reviewMemo = record.memo ?: "",
            changeLog = logs.flatMap(::toChangeLogEntries),
        )
    }

    /** 이력 1건 → export 항목. 편집은 필드 diff별로, 전이는 review_status 변화 1건으로 평탄화. */
    private fun toChangeLogEntries(log: InspectionChangeLog): List<ExportChangeLogEntry> = when (log.type) {
        InspectionChangeType.EDIT -> log.changes.map { change ->
            ExportChangeLogEntry(
                at = log.createdAt.kst(),
                field = ExportSchema.fieldId(change.field),
                from = change.before,
                to = change.after,
                action = "edit",
            )
        }

        InspectionChangeType.CONFIRM, InspectionChangeType.REJECT -> listOf(
            ExportChangeLogEntry(
                at = log.createdAt.kst(),
                field = "review_status",
                from = log.fromStatus?.let { ExportSchema.reviewStatus(it) },
                to = log.toStatus?.let { ExportSchema.reviewStatus(it) },
                action = log.type.name.lowercase(),
            ),
        )
    }

    /** 저장된 LocalDateTime(서버 로컬 = KST 가정)을 +09:00 오프셋으로. */
    private fun LocalDateTime.kst(): OffsetDateTime = atOffset(ZoneOffset.of("+09:00"))
}
