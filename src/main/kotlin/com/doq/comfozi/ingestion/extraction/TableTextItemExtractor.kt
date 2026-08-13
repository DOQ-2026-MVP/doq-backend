package com.doq.comfozi.ingestion.extraction

/**
 * 공문 텍스트에서 단가표 행을 뽑는 규칙 기반 추출기 — LLM 없이 도는 기본 경로.
 *
 * 뽑은 값은 **관찰값**이다. 틀릴 수 있고, 그래서 검수 단계가 있다 — 사람이 원본과 대조해 고친다.
 * 비워 두는 쪽이 안전해 보이지만 그건 일을 전부 사람에게 넘기는 것이라, 확신이 서는 만큼은 채운다.
 * 열을 잘못 잡으면 대개 예외 탐지(`unit_mismatch`·`spec_mismatch`)에 걸려 검수자 눈에 띈다.
 *
 * **읽어낸 표 행이 하나도 없으면 [RawTextItemExtractor] 로 내려간다** — 서식이 달라 못 읽었으면
 * 원문이라도 보여주는 게 낫다.
 *
 * 파싱은 **오른쪽부터** 맞춘다. 품목명·규격은 공백을 품을 수 있어 왼쪽에서 세면 어긋나지만,
 * 오른쪽 끝(적용일자·인상률)·단가·단위는 모양이 뚜렷하다.
 *
 * ```
 * 토마토살사S/O 4kg/PK PK 32,000 33,600 2026-08-01
 * └── 품목명 ──┘└규격┘ └단위┘└기존┘ └변경┘ └─적용일─┘
 *
 * 1 냉동 다진마늘 1 kg 봉 7,200원 7,560원 5.0%
 * │ └─ 품목명 ─┘└규격┘└단위┘└기존┘ └변경┘ └인상률┘   ← 적용일자 열이 없는 서식
 * 번호                                                    (적용일은 머리말 `시행일` 에서)
 * ```
 */
object TableTextItemExtractor : ItemExtractor {

    override fun extract(fileName: String, text: String): List<ExtractedItem> {
        val lines = text.lines()
        val supplier = lines.firstNotNullOfOrNull(::supplierOf)
        val headerDate = lines.firstNotNullOfOrNull(::effectiveDateOf)

        val items = lines.mapNotNull { toItem(it, supplier, headerDate) }

        // 서식이 달라 한 줄도 못 읽었으면 원문이라도 넘긴다
        return items.ifEmpty { RawTextItemExtractor.extract(fileName, text) }
    }

    private fun toItem(line: String, supplier: String?, headerDate: String?): ExtractedItem? {
        val row = ROW.matchEntire(line.trim().replace('　', ' ').trim()) ?: return null
        val (itemName, spec) = splitNameAndSpec(row.groupValues[1])

        // 마지막 칸이 날짜면 그게 적용일, 인상률이면 문서 머리말의 시행일을 쓴다
        val tail = row.groupValues[5].value()
        return ExtractedItem(
            supplier = supplier?.value(),
            rawItemName = itemName.value(),
            spec = spec?.value(),
            unit = row.groupValues[2].value(),
            priceBefore = row.groupValues[3].value(),
            priceAfter = row.groupValues[4].value(),
            effectiveDate = if (tail != null && DATE.matches(tail)) tail else headerDate?.value(),
        )
    }

    /**
     * 칸에서 꺼낸 값 다듬기 — 앞뒤 공백을 떼고 사이 공백은 한 칸으로 줄인다.
     *
     * OCR 은 열을 공백으로 **정렬**해 넣기 때문에 값에 공백이 길게 딸려 온다(`52,000       `).
     * 그대로 두면 원문 비교(중복 판정)가 어긋나고 화면에도 그렇게 보인다. 빈 값은 null 로 본다.
     */
    private fun String.value(): String? = trim().replace(Regex("\\s+"), " ").ifBlank { null }

    /**
     * 왼쪽 잔여를 품목명과 규격으로 가른다 — **숫자로 시작하는 첫 토큰부터가 규격**이다.
     * (`나초칩454G` 처럼 숫자를 품기만 한 이름은 갈라지지 않는다.)
     *
     * 다만 규격이 `기존 10kg / 변경 9kg` 처럼 **한글로 시작하는 변경 통보**면 숫자 규칙이 어긋난다
     * (`냉동돈전지 기존` 이 품목명이 돼버린다). 그 형태가 보이면 `기존` 부터를 규격으로 본다 —
     * 그래야 규격 변경 예외(spec_mismatch)가 제대로 물린다.
     */
    private fun splitNameAndSpec(left: String): Pair<String, String?> {
        val tokens = left.split(' ').filter { it.isNotBlank() }
        val specAt = SPEC_CHANGE.find(left)
            ?.let { tokens.indexOfFirst { t -> t == "기존" } }
            ?: tokens.indexOfFirst { it.first().isDigit() }
        if (specAt <= 0) return left to null // 규격을 못 가르면 통째로 품목명

        return tokens.take(specAt).joinToString(" ") to tokens.drop(specAt).joinToString(" ")
    }

    /**
     * `발신 한들푸드유통 식자재사업부` → `한들푸드유통` (부서명은 뒤에 온다).
     * 거래명세서류는 `발신` 대신 `공급자` 를 쓰기도 한다.
     */
    private fun supplierOf(line: String): String? =
        SENDER.find(line.replace('　', ' '))?.groupValues?.get(1)

    /** `시행일 2026년 8월 15일` — 표에 적용일자 열이 없는 서식에서 쓴다. */
    private fun effectiveDateOf(line: String): String? =
        HEADER_DATE.find(line.replace('　', ' '))?.groupValues?.get(1)

    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * 단가 한 칸 — **천 단위 콤마나 `원` 표기가 있어야** 한다. 미확정이면 `추후 안내`.
     *
     * 맨숫자를 받지 않는 이유: OCR 이 괘선을 글자로 읽어 `oe} 900 090` 같은 줄을 뱉는데,
     * 끝 칸이 선택이라 그런 잡음도 표 행 모양이 된다. 단가는 이 문서들에서 예외 없이 콤마나
     * `원` 을 달고 있어, 그걸 요구하면 잡음만 걸러진다.
     */
    private const val PRICE = """\d{1,3}(?:,\d{3})+\s*원?|\d+\s*원|추후\s*안내"""

    /** 단위 한 칸 — 글자·숫자만(`PK`·`BOX`·`봉`·`박스`). OCR 이 괘선을 읽은 `oe}`·`[ek` 은 아니다. */
    private const val UNIT = """[\p{L}\p{N}]{1,6}"""

    /**
     * 표 한 행 — 앞의 선택적 번호를 떼고 오른쪽부터 단위·기존단가·변경단가를 고정한다.
     * 단위·단가 모양을 좁게 잡아 OCR 잡음이 표 행으로 둔갑하지 않게 한다.
     *
     * 끝 칸(적용일자·인상률)은 **선택**이다. 적용일자 칸이 비어 있는 행이 실제로 있고
     * (요구사항 DOC-016), 그걸 버리면 그 항목이 통째로 사라진다 — 비어 있으면 비어 있는 대로
     * 올려 필수값 누락으로 드러나는 게 맞다.
     */
    private val ROW = Regex(
        """(?:\d+\s+)?(.+?)\s+($UNIT)\s+($PRICE)\s+($PRICE)(?:\s+(\d{4}-\d{2}-\d{2}|[\d.]+\s*%|-))?""",
    )

    /** `발 신 …` 또는 `공 급 자 …` — 앞의 `공급받는자`(수신처)와 헷갈리지 않게 경계를 둔다. */
    private val SENDER = Regex("""(?:^|\s)(?:발\s*신|공\s*급\s*자)\s+(\S+)""")

    /** 규격이 `기존 … / 변경 …` 인 변경 통보 (AnomalyRule.SpecChangePattern 과 같은 형태). */
    private val SPEC_CHANGE = Regex("""기존\s+.+?\s*/\s*변경\s+""")
    private val HEADER_DATE = Regex("""시행일\s+(\d{4}년\s*\d{1,2}월\s*\d{1,2}일)""")
}
