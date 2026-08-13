package com.doq.comfozi.inspection.domain

import com.doq.comfozi.structuring.mapping.MappedRecord
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.reflect.KProperty1

/**
 * 편집으로 바뀐 필드 1개 — 이전값→이후값. (전체 스냅샷이 아니라 변경분만 이력에 남긴다)
 *
 * 필드는 [MappedRecord] 프로퍼티 참조([property])로 들고, [field] 이름은 거기서 유도한다
 * (매직 스트링 방지). jsonb 직렬화는 `{field, before, after}` 형태 — 역직렬화는 [fromJson]으로 복원.
 */
data class FieldChange(
    @get:JsonIgnore
    val property: KProperty1<MappedRecord, String?>,
    val before: String?,
    val after: String?,
) {
    val field: String
        @JsonProperty("field") get() = property.name

    companion object {
        /** 캐노니컬 필드 순서 — diff·복원의 단일 출처. 필수 9필드 + 정규화 품목명. */
        val FIELDS: List<KProperty1<MappedRecord, String?>> =
            MappedRecord.REQUIRED_FIELDS + MappedRecord::normalizedItemName

        private val BY_NAME: Map<String, KProperty1<MappedRecord, String?>> = FIELDS.associateBy { it.name }

        /** jsonb 역직렬화 — 저장된 필드 이름을 프로퍼티 참조로 복원. */
        @JvmStatic
        @JsonCreator
        fun fromJson(
            @JsonProperty("field") field: String,
            @JsonProperty("before") before: String?,
            @JsonProperty("after") after: String?,
        ): FieldChange = FieldChange(BY_NAME.getValue(field), before, after)
    }
}

/** 두 편집본의 필드 단위 diff — 값이 달라진 필드만 반환한다(순서는 캐노니컬 필드 순서). */
fun diffFields(before: MappedRecord, after: MappedRecord): List<FieldChange> =
    FieldChange.FIELDS.mapNotNull { property ->
        val b = property.get(before)
        val a = property.get(after)
        if (b != a) FieldChange(property, b, a) else null
    }
