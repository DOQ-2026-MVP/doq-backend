package com.doq.comfozi.ingestion.domain

/**
 * 증빙의 **원래 유형** — 원본 행 `content["sourceType"]` 에 담기는 값의 어휘.
 *
 * 확장자가 아니라 **범주**다. PNG·JPG·JPEG 는 모두 [IMAGE] 한 칸으로 들어온다 — 검수 화면이
 * 고르는 단위가 범주이고, 정확한 포맷이 필요하면 업로드 파일명(`ingestion_upload.file_name`)이
 * 이미 들고 있다. 확장자를 그대로 흘리면 같은 뜻의 값이 PNG·JPEG·IMAGE 로 갈라져 쌓인다.
 *
 * 주의: 업로드 **경로**를 뜻하는 [IngestionUploadType] 과 다르다.
 */
enum class SourceType {
    XLSX,
    CSV,
    PDF,
    IMAGE,
    MANUAL,
    ;

    companion object {

        /** 확장자 표기 → 범주. 분류기가 낼 수 있는 이미지 포맷들. */
        private val ALIAS: Map<String, SourceType> = mapOf(
            "PNG" to IMAGE,
            "JPG" to IMAGE,
            "JPEG" to IMAGE,
        )

        /**
         * 바깥에서 들어온 표기를 어휘로 맞춘다 — 대소문자와 확장자 표기를 흡수한다.
         *
         * 모르는 값이면 null 이다. 버릴지 원문을 남길지는 호출부가 정한다 — 사용자가 취합 표에
         * 적어 넣은 관찰값은 원문 그대로 두는 것이 이 시스템의 규칙이라(MappedRecord) 여기서
         * 일괄로 정하지 않는다.
         */
        fun from(raw: String?): SourceType? {
            val key = raw?.trim()?.uppercase().orEmpty()
            if (key.isEmpty()) return null
            return ALIAS[key] ?: entries.firstOrNull { it.name == key }
        }

        /** 아는 표기면 어휘로 맞추고, 모르면 원문 그대로 — 값을 잃지 않는 정규화. */
        fun normalizeOrKeep(raw: String?): String? = from(raw)?.name ?: raw
    }
}
