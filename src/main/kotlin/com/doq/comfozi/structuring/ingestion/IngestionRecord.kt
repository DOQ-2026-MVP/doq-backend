package com.doq.comfozi.structuring.ingestion

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 인입 원본 행 (Postgres) — [Ingestion] 세션 안의 구조화 대상 한 행.
 *
 * 출처는 [uploadRef]로 추적한다 (어느 업로드에서, BATCH_FILE이면 몇 번째 행에서 왔는지).
 * [uploadRef]가 null이면 **수기 입력**(업로드 없이 record만 생성).
 * 모든 데이터 값은 **원문 그대로(raw String)** 보관 — 누락·형식오류(빈 적용일, "기존/변경" 규격,
 * 콤마 단가 등)도 인입 시점엔 거르지 않고, 이후 structuring에서 검증·정규화·예외 탐지한다.
 *
 * 컬럼 키셋은 요구사항 §입력 파일 컬럼(9개) 기준.
 * NOTE: 필드 잠정. 스키마는 Flyway V1 DDL로 별도 작성.
 */
@Entity
@Table(name = "ingestion_record")
class IngestionRecord(
    @Column(nullable = false, updatable = false)
    val ingestionId: Long,

    // null = 수기 입력 (업로드 출처 없음)
    @Embedded
    val uploadRef: IngestionUploadRef? = null,

    // 원문 그대로 (검증 전)
    @Column(columnDefinition = "text") val docId: String? = null,          // 문서ID
    @Column(columnDefinition = "text") val sourceType: String? = null,     // 원본유형 (PDF·XLSX·IMAGE·수기)
    @Column(columnDefinition = "text") val supplier: String? = null,       // 공급사
    @Column(columnDefinition = "text") val rawItemName: String? = null,    // 원문 품목명
    @Column(columnDefinition = "text") val spec: String? = null,           // 규격
    @Column(columnDefinition = "text") val unit: String? = null,           // 단위
    @Column(columnDefinition = "text") val priceBefore: String? = null,    // 기존단가
    @Column(columnDefinition = "text") val priceAfter: String? = null,     // 변경단가
    @Column(columnDefinition = "text") val effectiveDate: String? = null,  // 적용일
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
