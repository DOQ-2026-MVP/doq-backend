package com.doq.comfozi.structuring.ingestion.domain

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
 * [rowNo]는 BATCH_FILE이면 파일 행 번호(헤더=1행), FILE이면 한 문서에서 추출한 항목의 순번(1-base)이다.
 * 한 PDF에서 여러 항목이 나와도 같은 파일명을 공유하고 [rowNo]로 구분된다(요구사항 §원본 파일 구조).
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
