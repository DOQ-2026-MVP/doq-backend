package com.doq.comfozi.ingestion.service

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

/**
 * 변화의 계기. 결과 상태는 세션 현황에서 읽으므로 여기서 중복해 싣지 않는다.
 *
 * **세션 내용을 바꾸는 동작은 빠짐없이 여기에 있어야 한다.** 하나라도 빠지면 그 동작을 한 사람의
 * 화면만 최신이고 같은 세션을 보고 있는 다른 화면은 조용히 낡는다.
 */
sealed interface IngestionChange {

    /** 업로드 접수됨 — 원본 보관 완료, 파싱·추출은 아직(PARSING). */
    data class UploadReceived(val uploadId: Long) : IngestionChange

    /** 업로드 처리 끝남 — 성공(PARSED)인지 실패(PARSE_FAILED)인지는 업로드 현황에 있다. */
    data class UploadSettled(val uploadId: Long) : IngestionChange

    /** 업로드 삭제됨 — 그 업로드에서 나온 행과 저장 원본까지 함께. */
    data class UploadDeleted(val uploadId: Long) : IngestionChange

    /** 수기 행 저장됨. */
    data class RecordsAdded(val addedCount: Int) : IngestionChange

    /** 수기 행의 원문이 교체됨. */
    data class RecordUpdated(val recordId: Long) : IngestionChange

    /** 원본 행 1건 삭제됨 (수기·파일 무관). */
    data class RecordDeleted(val recordId: Long) : IngestionChange

    /** 세션이 비워짐 — 원본 행·업로드·저장 파일 전부. */
    data object SessionCleared : IngestionChange
}
