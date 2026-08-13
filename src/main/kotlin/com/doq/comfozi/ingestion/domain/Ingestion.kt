package com.doq.comfozi.ingestion.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 인입 세션 (Postgres) — 한 작업 단위. 취합 CSV·원본 파일(PDF/이미지)·수기 입력이 이 세션에 섞여 담긴다.
 *
 * - [IngestionUpload] (1:N) : 각 파일 업로드 (BATCH_FILE / FILE)
 * - [IngestionRecord] (1:N) : 구조화 대상 원본 행. 파일 출처는 [IngestionRecord.uploadRef]로 가리키고,
 *                             수기 입력은 업로드 없이 record만 생성(uploadRef=null)
 *
 * NOTE: 필드 잠정. 스키마는 Flyway V1 DDL로 별도 작성 (ddl-auto=validate).
 */
@Entity
@Table(name = "ingestion")
class Ingestion(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: IngestionStatus = IngestionStatus.DRAFT,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 구조화 실패 → [IngestionStatus.FAILED]. 종료(STRUCTURED)만 아니면 가능. 이후 재시도 가능. */
    fun markFailed() {
        check(status != IngestionStatus.STRUCTURED) { "실패로 전이할 수 없는 상태: $status" }
        status = IngestionStatus.FAILED
    }

    /** 구조화 성공 → [IngestionStatus.STRUCTURED]. DRAFT(최초) 또는 FAILED(재시도)에서. */
    fun markStructured() {
        check(status == IngestionStatus.DRAFT || status == IngestionStatus.FAILED) {
            "구조화 완료로 전이할 수 없는 상태: $status"
        }
        status = IngestionStatus.STRUCTURED
    }

    /** 입력이 바뀌어(업로드 삭제 등) 재검증이 필요 → [IngestionStatus.DRAFT]로 되돌린다. 종료(STRUCTURED)면 불가. */
    fun reopen() {
        check(status != IngestionStatus.STRUCTURED) { "완료된(STRUCTURED) 세션은 되돌릴 수 없음" }
        status = IngestionStatus.DRAFT
    }
}
