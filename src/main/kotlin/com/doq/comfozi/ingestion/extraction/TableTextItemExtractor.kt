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
        val tail = row.groupValues[5]
        return ExtractedItem(
            supplier = supplier,
            rawItemName = itemName,
            spec = spec,
            unit = row.groupValues[2],
            priceBefore = row.groupValues[3],
            priceAfter = row.groupValues[4],
            effectiveDate = if (DATE.matches(tail)) tail else headerDate,
        )
    }

    /**
     * 왼쪽 잔여를 품목명과 규격으로 가른다 — **숫자로 시작하는 첫 토큰부터가 규격**이다.
     * (`나초칩454G` 처럼 숫자를 품기만 한 이름은 갈라지지 않는다.)
     */
    private fun splitNameAndSpec(left: String): Pair<String, String?> {
        val tokens = left.split(' ').filter { it.isNotBlank() }
        val specAt = tokens.indexOfFirst { it.first().isDigit() }
        if (specAt <= 0) return left to null // 규격을 못 가르면 통째로 품목명

        return tokens.take(specAt).joinToString(" ") to tokens.drop(specAt).joinToString(" ")
    }

    /** `발신 한들푸드유통 식자재사업부` → `한들푸드유통` (부서명은 뒤에 온다). */
    private fun supplierOf(line: String): String? =
        SENDER.find(line.replace('　', ' '))?.groupValues?.get(1)

    /** `시행일 2026년 8월 15일` — 표에 적용일자 열이 없는 서식에서 쓴다. */
    private fun effectiveDateOf(line: String): String? =
        HEADER_DATE.find(line.replace('　', ' '))?.groupValues?.get(1)

    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    /** 단가 한 칸 — 숫자(콤마·`원` 허용)이거나 `추후 안내` 처럼 미확정 표기. */
    private const val PRICE = """[\d,]+\s*원?|추후\s*안내"""

    /** 표 한 행 — 앞의 선택적 번호를 떼고, 오른쪽 4칸(단위·기존·변경·끝)을 고정한다. */
    private val ROW = Regex(
        """(?:\d+\s+)?(.+?)\s+(\S+)\s+($PRICE)\s+($PRICE)\s+(\d{4}-\d{2}-\d{2}|[\d.]+\s*%|-)""",
    )

    private val SENDER = Regex("""발\s*신\s+(\S+)""")
    private val HEADER_DATE = Regex("""시행일\s+(\d{4}년\s*\d{1,2}월\s*\d{1,2}일)""")
}
