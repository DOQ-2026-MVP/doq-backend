---
name: mvp-requirements
description: 이 프로젝트(ComfoziAI 구매 증빙 인박스)의 MVP 요구사항 원본을 docs/requirements/ 에서 읽는다. 다음 상황에서 사용 - MVP 범위·요구사항·명세·"요구사항 정의서"·"규칙 명세"·"기대 결과표"를 확인할 때, 예외 탐지 규칙(missing_required·spec_mismatch·unit_mismatch·duplicate_suspected)의 판정 기준을 확인할 때, 필수 9필드·정규화 규칙·품목 마스터 사전·검수 상태 전이·승인 조건·export 스키마의 정의를 확인할 때, 제공 20건(DOC-001~DOC-020)의 기대 추출상태/기대 검수결과를 확인할 때, 현재 구현이 명세를 만족하는지 검증할 때. 명세와 무관한 리팩터링·빌드 오류·의존성 문제에는 사용하지 않는다.
---

# MVP 요구사항 조회

`docs/requirements/` 가 이 프로젝트의 **정답 기준**이다. 발주처(Comfozi) 원본 자료가 들어 있고,
gitignore 되어 저장소에는 커밋되지 않는다 — 그래서 로컬에 있을 수도, 없을 수도 있다.

## 1. 관심사부터 고른다

원본은 `.md` 3개 + `.xlsx` + `.csv` 이고 통째로 읽으면 크다.
**`docs/requirements/topics/` 의 관심사별 요약을 먼저 읽는다.** 각 요약 하단에 원본 출처(`파일 §절`)가
있으니, 근거가 더 필요할 때만 원본으로 내려간다.

| 질문이 이런 거라면 | 읽을 파일 |
|---|---|
| 뭘 만들어야 하나 · 범위 안인가 · 금지 사항 · 평가 기준 · 인계물 | `topics/scope-and-acceptance.md` |
| 필수 9필드 · 입력 형식 · 처리 순서 · 원본 근거(source_ref) | `topics/input-and-ingestion.md` |
| 정규화 품목명 · 품목 마스터 사전 · "데이터 부족" | `topics/normalization.md` |
| 예외 4종 판정 조건 · 규격 정규식 · 표준 단위 집합 · 중복 키 | `topics/exception-rules.md` |
| 검수 상태 전이 · 승인 조건·차단 · 해소 vs 수용 · 변경 이력 | `topics/review-and-approval.md` |
| export JSON·CSV 필드 · enum 값 | `topics/export-schema.md` |
| DOC-0xx 기대 결과 · 예외 4건 · 함정 | `topics/expected-results.md` |

애매하면 `docs/requirements/INDEX.md` 를 먼저 읽는다 (관심사 표 + 원본 목록 + 문서 우선순위).

**디렉터리가 없거나 비어 있으면 거기서 멈춘다.** 사용자에게 이렇게 알린다:

> 요구사항 원본이 아직 로컬에 없습니다. `docs/requirements/` 에 문서를 넣어주세요.

`README.md` 나 `docs/설계.md` 로 대신 답하지 않는다 — 그건 *구현 현황*이지 명세가 아니다.
근거가 없으면 추측하지 말고 없다고 말한다.

## 2. 원본으로 내려갈 때

요약에 없는 세부·정확한 문구·표 전체가 필요할 때만.

| 확장자 | 읽는 방법 |
|---|---|
| `.md` | Read 도구 |
| `.csv` | 작으면 Read 도구, 크면 아래 스크립트 |
| `.xlsx` | 아래 스크립트 (바이너리라 Read 도구로는 못 읽는다) |

파일명에 공백·한글이 있으므로 **경로를 따옴표로 감싼다.**

```bash
# 시트 목록
python3 .claude/skills/mvp-requirements/scripts/read_sheet.py "docs/requirements/컴포지 요구사항 정의서.xlsx"

# 시트를 마크다운 표로 (--sheet 은 시트명 또는 0-기반 인덱스)
python3 .claude/skills/mvp-requirements/scripts/read_sheet.py "docs/requirements/컴포지 요구사항 정의서.xlsx" --sheet 1 --max-rows 999

# CSV 도 같은 도구로 (기본 100행)
python3 .claude/skills/mvp-requirements/scripts/read_sheet.py "docs/requirements/42_해커톤_업로드용_증빙20건_2026-08-04.csv"
```

## 3. 답할 때

- **출처를 남긴다** — `43_해커톤_개발팀문의답변_규칙명세_2026-08-04.md §4-2`, `컴포지 요구사항 정의서.xlsx 4. 세부자료!7)` 처럼
  파일명과 위치를 함께 적는다. 사용자가 원본을 다시 찾을 수 있어야 한다.
  `topics/` 요약만 보고 답했으면 그 사실도 밝힌다.
- **원본이 이긴다** — `docs/requirements/` 와 `README.md`·`docs/설계.md`·코드가 어긋나면
  요구사항 문서를 따르고, **어긋났다는 사실 자체를 사용자에게 보고**한다. 조용히 넘기지 않는다.
- **날짜가 늦은 문서가 우선** — `08-04` → `08-06`(정정) → `08-09`(명확화).
  특히 20건 기대 결과표는 `43_해커톤_개발팀문의답변_규칙명세_2026-08-04.md` §6-2 가 아니라
  `43_해커톤_개발팀문의답변_규칙명세_2026-08-06.md` §4 가 유효하다.
- **규칙 조항 > 요약표** — 발주처가 명시한 원칙이다. 실제로 §6-2 표가 §4 조항과 어긋나 정정된 전례가 있다.

## 4. 쓰지 않는다

이 스킬은 읽기 전용이다. `docs/requirements/` 안의 파일을 만들거나 고치지 않는다.

`topics/` 요약은 원본에서 파생된 것이므로, **원본이 갱신되면 요약도 갱신이 필요**하다.
새 공지 파일이 들어왔는데 관련 `topics/` 문서가 그 내용을 반영하지 않았다면 사용자에게 알린다.
