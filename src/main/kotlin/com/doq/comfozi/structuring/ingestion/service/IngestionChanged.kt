package com.doq.comfozi.structuring.ingestion.service

/**
 * 인입 세션에 **커밋된** 변화 1건 — 세션을 보고 있는 화면에 알릴 거리.
 *
 * 적재·처리 경로에서 발행하고 스트림(`IngestionEventStream`)이 커밋 이후 받아 구독자에게 흘린다.
 * 변화의 **내용은 싣지 않는다** — 받는 쪽이 그때의 세션 현황을 다시 읽어 통째로 내보내므로,
 * 이 이벤트는 "무엇이 계기였는지"만 말한다(화면 알림 문구용).
 */
data class IngestionChanged(
    val ingestionId: Long,
    val change: IngestionChange,
)

/** 변화의 계기. 결과 상태는 세션 현황에서 읽으므로 여기서 중복해 싣지 않는다. */
sealed interface IngestionChange {

    /** 업로드 접수됨 — 원본 보관 완료, 파싱·추출은 아직(PARSING). */
    data class UploadReceived(val uploadId: Long) : IngestionChange

    /** 업로드 처리 끝남 — 성공(PARSED)인지 실패(PARSE_FAILED)인지는 업로드 현황에 있다. */
    data class UploadSettled(val uploadId: Long) : IngestionChange

    /** 수기 행 저장됨. */
    data class RecordsAdded(val addedCount: Int) : IngestionChange
}
