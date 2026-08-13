# DOQ Backend — ComfoziAI 구매 증빙 인박스

ComfoziAI 구매 증빙 인박스의 **앞단 2단계** 백엔드. XLSX/CSV·수기 입력을 검수 가능한
후보로 **구조화**하고, 사람이 **검수·승인/반려**한 항목만 ComfoziAI 전달용 JSON·CSV로
**export**한다. (가격 점검 이후 단계는 범위 밖 — 기존 ComfoziAI가 이 출력을 받아 시작한다.)

- `structuring` — 기계 단계: 인입(ingestion) → 매핑(mapping) → 정규화(normalization) → 예외 탐지(detection)
- `inspection` — 사람 단계: 검수 인박스 영속 → 편집·확정/반려·변경 이력 → export

## 요구 사항

- JDK 21 이상 (미설치 시 Gradle toolchain 이 foojay 로 자동 다운로드)
- Docker / Docker Compose (로컬 PostgreSQL 자동 실행에 사용)

## 실행 (자동 런칭)

```bash
./gradlew bootRun     # compose.yaml 의 postgres 를 띄우고 앱 실행
```

- `spring-boot-docker-compose` 로 `compose.yaml` 의 **PostgreSQL 컨테이너가 자동 기동**되고
  접속 정보가 앱에 주입됩니다. 별도 DB 설치·환경변수 불필요.
- 컨테이너가 `healthy` 가 될 때까지 대기 후 부팅하며, 앱 종료 시 컨테이너도 함께 종료됩니다.
- 스키마는 **Flyway** 마이그레이션(`db/migration`)으로 적용됩니다 (`ddl-auto: validate`).

### 실행 확인

```bash
curl -s localhost:8080/actuator/health   # {"status":"UP"}
```

- **Swagger UI**: http://localhost:8080/swagger-ui.html — 전체 API 를 브라우저에서 호출해볼 수 있습니다.
- OpenAPI 문서: http://localhost:8080/v3/api-docs

## 전체 흐름 (제공 20건 기준)

```
업로드/수기 입력          구조화(기계)                     검수(사람)                    전달
POST /api/ingestion  →  POST /api/structuring/{id}  →  PATCH/POST /api/inspection  →  GET .../export.json|csv
   (원본 행 적재)         (매핑·정규화·탐지 → 인계)        (편집·확정/반려·이력)          (승인 항목만)
```

## API 요약

베이스 응답은 `{ success, data, error }` envelope (**export 제외** — export 는 원본 파일 그대로 다운로드).

### 인입 (`/api/ingestion`)
| Method | Path | 설명 |
|---|---|---|
| POST | `/uploads` (multipart `file`) | 취합 파일(CSV/XLSX) 업로드 → 새 세션 + 원본 행 적재 |
| POST | `/{ingestionId}/uploads` | 기존 DRAFT 세션에 파일 이어붙임 |
| POST | `/records` | 수기 입력들로 새 세션 |
| POST | `/{ingestionId}/records` | 수기 입력 이어붙임 |
| PUT | `/{ingestionId}/records/{recordId}` | 수기 행 수정 (9필드 전체 교체) |
| DELETE | `/{ingestionId}/records/{recordId}` | 원본 행 1건 삭제 (수기·파일 무관) |
| DELETE | `/{ingestionId}/uploads/{uploadId}` | 업로드 1건 삭제 (해당 업로드의 원본 행·저장 파일 포함) |
| DELETE | `/{ingestionId}/records` | 세션 비우기(truncate → DRAFT 복귀) |
| GET | `/{ingestionId}` | 세션 + 업로드 현황 + 원본 행 조회 |

입력 변경(삭제·수정)은 **완료(STRUCTURED) 세션에서는 409** 다 — 구조화 이후의 수정은 검수(inspection) 도메인의 몫이다.
DRAFT·FAILED 세션에서는 가능하며, 입력이 바뀌었으므로 세션은 DRAFT 로 되돌아간다.

**파일 출처 행은 수정할 수 없다(409).** 원문이 곧 원본 근거(관찰값)라 인입 단계에서 덮으면
검수의 "관찰값 vs 수정값" 분리가 무의미해지기 때문 — 구조화 후 `PATCH /api/inspection/records/{id}` 로 고친다.
삭제는 구조화 전이라 깨지는 불변식이 없어 출처와 무관하게 허용한다.

#### 세션 조회 응답 — 입력 화면용 현황

`GET /{ingestionId}` 은 `uploads`(업로드 현황)와 `records`(원본 행)를 함께 준다.
변경 계열 응답(업로드·수기 입력)은 세션만 돌려주므로 둘 다 `null` 이다.

| 필드 | 설명 |
|---|---|
| `uploads[].status` | `PARSED` (취합 파일 파싱 완료) · `PENDING_EXTRACTION` (원본 문서 보관, 행 추출 미지원) |
| `uploads[].recordCount` | 그 업로드에서 나온 원본 행 수 (수기 입력은 세지 않음) |
| `records[].uploadId` / `uploadType` / `uploadRowNo` | 원본 근거. 수기 입력이면 전부 `null` |

파싱에 실패한 업로드는 애초에 저장하지 않으므로(업로드 요청이 400) `uploads` 에 나타나지 않는다.

**행 단위 이상 여부는 인입 단계에서 판정하지 않는다.** 필수값 누락·규격/단위 불일치·중복은
구조화 이후 검수 인박스에서 `exception_flags` 와 검수결과(`확인 필요`·`보류 필요`)로 표시된다.
수기 입력은 경계에서 9필드를 검증하므로(실패 시 400) 저장된 수기 행은 항상 완전하다.

### 구조화 (`/api/structuring`)
| Method | Path | 설명 |
|---|---|---|
| POST | `/{ingestionId}` | 구조화 실행 — 매핑·정규화·탐지 후 검수 인박스로 인계 |

### 검수 (`/api/inspection`)
| Method | Path | 설명 |
|---|---|---|
| GET | `?ingestionId={id}` | 검수 상세(레코드 포함) — ingestionId 로 |
| GET | `/{inspectionId}` | 검수 상세 — inspectionId 로 |
| GET | `/records/{recordId}/changelog` | 레코드 변경 이력(시각순) |
| PATCH | `/records/{recordId}` | 레코드 편집 — 편집본(current) 교체 (확정된 레코드면 409) |
| POST | `/records/{recordId}/confirm` | 확정 (선택 `{"memo":"…"}`) · **필수값 누락 시 409** |
| POST | `/records/{recordId}/reject` | 반려 (선택 `{"memo":"…"}`) |
| POST | `/{inspectionId}/confirm` | 남은 NEW 레코드 일괄 확정(필수값 누락은 건너뜀) |
| GET | `/{inspectionId}/export.json` | 승인 항목 JSON export |
| GET | `/{inspectionId}/export.csv` | 승인 항목 UTF-8 CSV export |

## 예외 탐지 규칙 (4종, 규칙 기반)

| 플래그(`exception_flags`) | 판정 | 결과 상태 |
|---|---|---|
| `missing_required` | 필수 9필드 중 하나라도 공란·공백 | 확인 필요 |
| `spec_mismatch` | 규격이 `기존 … / 변경 …` 패턴 | 보류 필요 |
| `unit_mismatch` | 단위가 표준집합(`PK`·`BOX`·`EA`·`PO`) 밖이거나 복수 단위 병기 | 보류 필요 |
| `duplicate_suspected` | 중복키 7필드 완전 일치, 그룹 내 2번째 이후 | 보류 필요 |

- 중복키 = 공급사 + 정규화 품목명 + 규격 + 단위 + 기존단가 + 변경단가 + 적용일, `문서ID` 오름차순 정렬 기준.
- 자동 병합·자동 승인은 하지 않는다. 사람이 편집·확정한 항목만 export 된다.

## 품목명 정규화 — 사전 사용 명시

원문 품목명 → 정규화 품목명 산출은 **품목 마스터 사전 조회**(제공 20건 기준) 방식이다
(`ItemNameNormalizer`). 사전에 없으면 빈칸이 아니라 **`"데이터 부족"`** 으로 표시하고,
검수 화면에서 사람이 교정할 수 있다. 규칙 기반 전개(약어·꼬리 제거)는 미구현이며,
요구사항상 자동 정규화 정확도는 평가 대상이 아니다(사전 20건으로 요건 충족).

## Export 스키마 (승인 항목만)

승인(`CONFIRMED`)된 레코드의 **편집본(current)** 을 영문 snake_case field ID 로 내보낸다.
승인 전·보류·반려는 제외. 승인 0건이면 빈 배열 / 헤더만 있는 CSV(200).

### JSON 예시 (`GET /api/inspection/{id}/export.json`)

```json
[
  {
    "doc_id": "DOC-001",
    "source_type": "PDF",
    "supplier_name": "가온푸드(예시)",
    "raw_item_name": "토마토살사S/O",
    "normalized_item_name": "토마토 살사 소스",
    "spec": "4kg/PK",
    "unit": "PK",
    "price_before": 32000,
    "price_after": 33600,
    "effective_date": "2026-08-01",
    "review_status": "approved",
    "exception_flags": [],
    "source_ref": { "input_method": "file", "file_name": "golden-20.csv", "row_no": 2 },
    "reviewed_at": "2026-08-10T14:03:00+09:00",
    "review_memo": "",
    "change_log": [
      { "at": "2026-08-10T14:01:00+09:00", "field": "normalized_item_name",
        "from": "토마토살사소스", "to": "토마토 살사 소스", "action": "edit" }
    ]
  }
]
```

### 필드 설명

| field | 타입 | 설명 |
|---|---|---|
| `doc_id` | string | 원본 증빙 식별자 (`DOC-###`) |
| `source_type` | string | 원본유형 (`PDF`·`XLSX`·`IMAGE`·`수기`) |
| `supplier_name` | string | 공급사명 |
| `raw_item_name` | string | 원문 품목명(공급사 표기 그대로) |
| `normalized_item_name` | string | 정규화 품목명(사전 산출, 실패 시 `"데이터 부족"`) |
| `spec` / `unit` | string | 규격 / 단위 |
| `price_before` / `price_after` | integer | 기존/변경 단가(정수) |
| `effective_date` | string | 적용일 (`YYYY-MM-DD`) |
| `review_status` | enum | 검수 상태 — export 는 항상 `approved` (그 외 `new`·`rejected`) |
| `exception_flags` | string[] | 이상 플래그 (`missing_required`·`duplicate_suspected`·`spec_mismatch`·`unit_mismatch`) |
| `source_ref` | object | 원본 근거 — `input_method`(`file`\|`manual`) · `file_name` · `row_no` |
| `reviewed_at` | string | 승인 시각 (ISO-8601, `+09:00`) |
| `review_memo` | string | 검수 메모(없으면 `""`) |
| `change_log` | object[] | 변경 이력 — `at` · `field` · `from` · `to` · `action`(`edit`\|`confirm`\|`reject`) |

### CSV (`GET /api/inspection/{id}/export.csv`)

- 위 필드를 **평탄화**한 UTF-8 CSV(엑셀 호환 BOM, RFC 4180 이스케이프).
- `source_ref` → `source_input_method` · `source_file_name` · `source_row_no` 컬럼.
- `exception_flags` → `|` 로 join. `change_log` 는 **CSV 미포함**(JSON 전용).

## 빌드 & 테스트

```bash
./gradlew build       # 컴파일 + 테스트
./gradlew test        # 테스트만 (H2 in-memory, Docker 불필요)
```

- 테스트 입력: `src/test/resources/fixtures/golden-20.csv` (제공 증빙 20건). 업로드→구조화→승인→export
  전 경로를 실제 파일로 검증한다.

## 배포

컨테이너 이미지 정의는 이 리포의 [`Dockerfile`](Dockerfile) 에 있고, 빌드된 이미지는
`ghcr.io/doq-2026-mvp/doq-backend` (public) 로 게시된다. `linux/amd64`, 태그는 커밋 sha 와 `latest`.

프론트·DB 까지 포함한 전체 스택을 한 번에 띄우려면
**[doq-deploy](https://github.com/DOQ-2026-MVP/doq-deploy)** 의 compose 구성을 쓴다.

- 이미지는 호스트에서 만든 jar 를 JRE 이미지에 복사만 하므로 빌드가 빠르다.
  컨테이너 TZ 는 `Asia/Seoul` 로 고정한다(`reviewed_at` 표기 때문).
- 운영 시 주입되는 환경변수는 아래 「접속 정보」의 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` /
  `STORAGE_LOCAL_ROOT` 이다.

## 지원 범위 · 미지원 · 알려진 제약

### 지원 (범위 IN)
- XLSX/CSV 업로드 + 수기 등록, 원본 행 근거(파일명·행번호)
- 구조화(매핑·사전 정규화·예외 4종 탐지), 검수 인박스 영속·조회
- 사람 편집·확정·반려·일괄확정, 필수값 누락 시 승인 차단, 변경 이력·메모
- 승인 항목 JSON+CSV export (수기·파일 두 경로)

### 미지원 / 범위 밖
- **PDF·이미지·EML 원본 문서 OCR** — 추가(선택) 요건, **미구현**.
- **정규화 규칙 엔진** — 사전 조회만 구현, 규칙 기반 전개(약어·꼬리 제거)는 미구현(사전 20건으로 요건 충족).
- **검수 인박스 상태/플래그 필터·목록 조회 API** — 프론트 클라이언트 필터 처리 전제로 미제공
  (검수 상세 응답이 레코드별 상태·플래그를 모두 포함).
- 가격 판정·비교·협상/RFQ, 회원/권한/인증, 실제 ComfoziAI Production 전송.
- 제공 20건을 벗어난 임의 입력 포맷 일반화.

### 알려진 제약 · 주의
- 업로드 원본 파일은 로컬 파일시스템(`./data/uploads`, `STORAGE_LOCAL_ROOT`)에 저장한다.
- `reviewed_at` 은 서버 로컬 시각을 `+09:00` 로 표기한다(서버 TZ 를 KST 로 가정).
- export 의 `file_name` 통합 테스트는 CSV 경로 기준(XLSX 도 동일 로직이나 별도 업로드 통합 테스트 없음).
- 인증·권한 없음(요구사항: 회원/권한 불필요). 비밀값·실데이터는 사용하지 않는다.

## 접속 정보 (compose 기본값)

| 서비스     | 값                                                     |
| ---------- | ------------------------------------------------------ |
| PostgreSQL | `localhost:5432` / db `doq` / user `doq` / pw `doq`    |

Docker Compose 없이 외부 DB 로 실행 시 환경변수로 오버라이드:

| 변수 | 기본값 |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/doq` |
| `DB_USERNAME` / `DB_PASSWORD` | `doq` / `doq` |
| `STORAGE_LOCAL_ROOT` | `./data/uploads` |

## 기술 스택

| 항목 | 버전 |
|---|---|
| Language | Kotlin 2.1 |
| Runtime | Java 21 (LTS, toolchain) |
| Framework | Spring Boot 3.5.x (Web MVC) |
| Build | Gradle 9.4 (Kotlin DSL) |
| RDB | PostgreSQL 16 + Spring Data JPA + Flyway |
| 파일 파싱 | Apache Commons CSV · Apache POI (XLSX) |
| API 문서 | springdoc OpenAPI (Swagger UI) |
| Test DB | H2 (in-memory) |
