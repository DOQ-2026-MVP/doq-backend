package com.doq.comfozi.ingestion.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

/**
 * 원본 근거 참조 (값 객체) — [IngestionRecord]가 어느 [IngestionUpload]에서 왔는지 가리킨다.
 *
 * **파일 출처(BATCH_FILE·FILE) record에만 존재**한다. 수기 입력 record는 [IngestionRecord.uploadRef]
 * 자체가 null이라 임베드 컬럼(upload_id/upload_type/upload_row_no)이 모두 비므로 null 허용.
 *
 * [uploadType]은 조인 없이 출처 표시가 가능하도록 의도적으로 중복 저장.
 * [rowNo]는 BATCH_FILE(취합 표 파일)에서 온 경우의 행 번호이며, FILE은 null.
 */
@Embeddable
class IngestionUploadRef(
    @Column(name = "upload_id")
    val uploadId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_type")
    val uploadType: IngestionUploadType,

    // BATCH_FILE 출처일 때만
    @Column(name = "upload_row_no")
    val rowNo: Int? = null,
)
