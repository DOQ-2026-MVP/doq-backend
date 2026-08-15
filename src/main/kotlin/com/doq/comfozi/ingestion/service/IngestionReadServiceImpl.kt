package com.doq.comfozi.ingestion.service

import com.doq.comfozi.ingestion.domain.Ingestion
import com.doq.comfozi.ingestion.domain.IngestionRecord
import com.doq.comfozi.ingestion.domain.IngestionUpload
import com.doq.comfozi.ingestion.repository.IngestionRecordRepository
import com.doq.comfozi.ingestion.repository.IngestionRepository
import com.doq.comfozi.ingestion.repository.IngestionUploadRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class IngestionReadServiceImpl(
    private val ingestionRepository: IngestionRepository,
    private val uploadRepository: IngestionUploadRepository,
    private val recordRepository: IngestionRecordRepository,
) : IngestionReadService {

    override fun getSessions(): List<IngestionSessionSummary> {
        val uploadCounts = uploadRepository.countGroupedByIngestionId().toCountMap()
        val recordCounts = recordRepository.countGroupedByIngestionId().toCountMap()

        return ingestionRepository.findAllByOrderByIdAsc().map { ingestion ->
            val id = requireNotNull(ingestion.id)
            IngestionSessionSummary(
                ingestion = ingestion,
                uploadCount = uploadCounts[id] ?: 0,
                recordCount = recordCounts[id] ?: 0,
            )
        }
    }

    /** `[ingestionId, count]` 튜플 목록을 조회용 맵으로. 집계가 없는 세션은 키가 아예 없다(=0). */
    private fun List<Array<Any>>.toCountMap(): Map<Long, Int> =
        associate { (it[0] as Number).toLong() to (it[1] as Number).toInt() }

    override fun getSession(ingestionId: Long): Ingestion =
        ingestionRepository.findByIdOrNull(ingestionId)
            ?: throw NoSuchElementException("알 수 없는 Ingestion 세션 $ingestionId")

    override fun getStatus(ingestionId: Long): IngestionSessionStatus {
        val manualRecords = recordRepository.findByIngestionIdAndUploadRefUploadIdIsNullOrderByIdAsc(ingestionId)

        return IngestionSessionStatus(
            ingestion = getSession(ingestionId),
            uploads = getUploads(ingestionId),
            manualRecords = manualRecords,
        )
    }

    override fun getUploads(ingestionId: Long): List<IngestionUpload> =
        uploadRepository.findByIngestionIdOrderByIdAsc(ingestionId)

    override fun getUpload(ingestionId: Long, uploadId: Long): IngestionUpload =
        uploadRepository.findByIdOrNull(uploadId)
            ?.takeIf { it.ingestionId == ingestionId }
            ?: throw NoSuchElementException("세션 $ingestionId 에 없는 업로드 $uploadId")

    override fun getRecords(ingestionId: Long): List<IngestionRecord> =
        recordRepository.findByIngestionIdOrderByIdAsc(ingestionId)
}
