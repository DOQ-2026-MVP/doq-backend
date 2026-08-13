package com.doq.comfozi.structuring.ingestion.api

import com.doq.comfozi.structuring.ingestion.service.IngestionChange

/**
 * 스트림이 내보내는 이벤트 — **그 시점 [state] 전체**와, 그것을 보낸 계기.
 *
 * 델타가 아니라 현황을 통째로 싣는다. 받는 쪽은 순서·유실·재연결을 따질 것 없이 화면을 갈아끼우면 되고,
 * 끊겼다 붙어도 첫 이벤트가 곧 최신 상태라 놓친 구간을 메울 필요가 없다.
 *
 * [state]는 변경 응답이 돌려주는 것과 **같은 타입**이다 — 화면은 어느 경로로 받았든 같은 모델을 다룬다.
 * [change]가 null이면 구독 직후의 최초 스냅샷이다.
 */
data class IngestionStateEvent(
    val state: IngestionState,
    val change: IngestionChangeResponse?,
)

/**
 * 이벤트를 보낸 계기 — 화면 알림 문구용. 결과 상태는 [IngestionStateEvent.state]에 있다.
 * [uploadId]/[recordId]/[addedCount]는 계기에 해당하는 것만 채워진다.
 */
data class IngestionChangeResponse(
    val type: Type,
    val uploadId: Long? = null,
    val recordId: Long? = null,
    val addedCount: Int? = null,
) {
    enum class Type {
        /** 업로드 접수됨 (원본 보관 완료, 처리 대기). */
        UPLOAD_RECEIVED,

        /** 업로드 처리 끝남 (성공·실패는 해당 업로드의 status). */
        UPLOAD_SETTLED,

        /** 업로드 삭제됨 (그 업로드의 행·저장 원본까지). */
        UPLOAD_DELETED,

        /** 수기 행 저장됨. */
        RECORDS_ADDED,

        /** 수기 행 원문이 교체됨. */
        RECORD_UPDATED,

        /** 원본 행 1건 삭제됨. */
        RECORD_DELETED,

        /** 세션이 비워짐. */
        SESSION_CLEARED,
    }

    companion object {
        /** `when` 이 분기를 강제하므로, 계기가 늘면 여기서 컴파일이 깨져 빠뜨릴 수 없다. */
        fun of(change: IngestionChange): IngestionChangeResponse = when (change) {
            is IngestionChange.UploadReceived ->
                IngestionChangeResponse(Type.UPLOAD_RECEIVED, uploadId = change.uploadId)

            is IngestionChange.UploadSettled ->
                IngestionChangeResponse(Type.UPLOAD_SETTLED, uploadId = change.uploadId)

            is IngestionChange.UploadDeleted ->
                IngestionChangeResponse(Type.UPLOAD_DELETED, uploadId = change.uploadId)

            is IngestionChange.RecordsAdded ->
                IngestionChangeResponse(Type.RECORDS_ADDED, addedCount = change.addedCount)

            is IngestionChange.RecordUpdated ->
                IngestionChangeResponse(Type.RECORD_UPDATED, recordId = change.recordId)

            is IngestionChange.RecordDeleted ->
                IngestionChangeResponse(Type.RECORD_DELETED, recordId = change.recordId)

            IngestionChange.SessionCleared ->
                IngestionChangeResponse(Type.SESSION_CLEARED)
        }
    }
}
