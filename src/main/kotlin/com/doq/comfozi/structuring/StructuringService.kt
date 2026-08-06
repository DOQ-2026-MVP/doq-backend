package com.doq.comfozi.structuring

/** 구조화 오케스트레이터 — ingestion 세션의 원문을 매핑·정규화·탐지해 구조화 결과로 인계한다. */
interface StructuringService {

    /**
     * [ingestionId] 세션의 원문 레코드들을 구조화한다:
     * 매핑(raw→캐노니컬) · 정규화(품목명) · 탐지(예외 4종, 중복은 집합 비교) →
     * 레코드별 [RecordStructured] 발행 → inspection이 InboxItem으로 영속.
     */
    fun struct(ingestionId: Long)
}
