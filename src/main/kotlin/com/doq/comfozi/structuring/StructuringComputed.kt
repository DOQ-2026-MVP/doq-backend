package com.doq.comfozi.structuring

/**
 * 구조화 계산 완료 — **커밋된 뒤** 결과를 검수 단계로 인계하라는 신호. structuring 내부용이다.
 *
 * 인계 계약인 [StructuredRecords]와 나눠 둔 이유: 이 이벤트는 "언제 인계할지"를 정하고
 * (커밋 이후·다른 스레드), [StructuredRecords]는 "무엇을 넘기는지"를 정한다. 하나로 합치면
 * 인계를 시작하는 발행과 검수가 받는 발행을 구분할 수 없어 같은 리스너가 자기 이벤트를 다시 받는다.
 */
data class StructuringComputed(
    val ingestionId: Long,
    val records: List<StructuredRecord>,
)
