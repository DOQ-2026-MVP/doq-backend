package com.doq.comfozi.ingestion.domain

/**
 * 업로드 처리 현황 — [IngestionUpload] 한 건이 **원본 행이 될 때까지** 어디까지 왔는가.
 *
 * 처리는 업로드 응답이 나간 뒤 **비동기로** 진행되므로 접수 직후에는 [PARSING]에 있다가
 * [PARSED] 또는 [PARSE_FAILED]로 끝난다. 실패도 **저장된 상태**다 — 응답은 이미 나갔으니
 * 실패를 알리는 곳이 여기뿐이고, 원본은 남아 있어 화면에서 확인·삭제할 수 있다.
 *
 * 취합 파일(파싱)과 원본 문서(추출)를 나누지 않고 같은 어휘를 쓴다 — 화면 입장에서는 둘 다
 * "이 파일이 행이 됐는가"이고, 원본 문서 추출이 붙어도 상태가 늘지 않는다.
 */
enum class IngestionUploadStatus {
    /** 접수 완료 — 파싱·추출 대기·진행 중. 아직 원본 행이 없다. */
    PARSING,

    /**
     * 처리 완료 — 추출·파싱이 끝나고 원본 행까지 적재됐다.
     *
     * 행 추출을 지원하지 않는 원본 문서(PDF·이미지)는 **행 0건**으로 여기 도달한다 —
     * 기계가 그 파일에 대해 할 일이 끝났다는 뜻이며, 행은 수기 입력으로 보완한다.
     */
    PARSED,

    /** 처리 실패 — 사유는 [IngestionUpload.failureReason]. 행은 없다. */
    PARSE_FAILED,

    ;

    /** 더 진행될 여지가 없는 상태인가 — 구조화 가능 여부 판단에 쓴다. */
    val isTerminal: Boolean get() = this != PARSING
}
