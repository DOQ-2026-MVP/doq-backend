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

**선택 — 이미지 항목 추출** (추가 요건): 이미지(PNG·JPEG)에서 글자를 뽑으려면 Tesseract 가 필요합니다.

```bash
brew install tesseract                    # macOS
# 한국어 학습데이터 — kor.traineddata 를 tessdata 디렉터리에 둔다
curl -sSL -o "$(brew --prefix)/share/tessdata/kor.traineddata" \
  https://github.com/tesseract-ocr/tessdata_fast/raw/main/kor.traineddata
```

Docker 이미지에는 이미 들어 있습니다(`tesseract-ocr`·`tesseract-ocr-kor`). 설치돼 있지 않으면
**이미지는 보관만 되고 부팅·업로드는 정상**입니다.

**선택 — LLM 항목 추출** (추가 요건):

```bash
ANTHROPIC_API_KEY=... ./gradlew bootRun
```

키를 주면 문서에서 뽑은 텍스트를 LLM 이 해석해 9필드로 적재합니다(규칙 파서 대신).

**키가 없어도 문서는 처리됩니다.** 규칙 기반 표 파서가 단가표를 읽어 항목별로 적재하고, 서식이 달라
한 줄도 못 읽으면 원문을 한 행에 담습니다. 어느 쪽이든 **관찰값**이라 검수에서 확인·수정됩니다.
필수 흐름(CSV/XLSX + 수기 입력)은 키와 무관하게 전부 동작합니다.

### 실행 확인

```bash
curl -s localhost:8080/actuator/health   # {"status":"UP"}
```

- **Swagger UI**: http://localhost:8080/swagger-ui.html — 전체 API 를 브라우저에서 호출해볼 수 있습니다.
- OpenAPI 문서: http://localhost:8080/v3/api-docs

## 전체 흐름

```
업로드/수기 입력          구조화(기계)                     검수(사람)                    전달
POST /api/ingestion  →  POST /api/structuring/{id}  →  PATCH/POST /api/inspection  →  GET .../export.json|csv
   (원본 행 적재)         (매핑·정규화·탐지 → 인계)        (편집·확정/반려·이력)          (승인 항목만)
```

엔드포인트·요청/응답 스키마의 단일 소스는 **Swagger UI** 다 — 컨트롤러에서 자동 생성되므로
이 문서에 API 표를 중복해 두지 않는다.

**파일 업로드는 접수까지만 동기다.** 원본을 보관하고 응답한 뒤, 커밋 이후 `IngestionUploadStored`
이벤트를 받아 별도 스레드에서 파싱·추출한다. 그래서 업로드가 끝난 시점에도 아직 행이 없을 수 있고
(`PARSING`), 처리 실패는 예외가 아니라 상태(`PARSE_FAILED` + 사유)로 남는다. 취합 파일과 원본 문서가
같은 상태 어휘를 쓰는데, 화면 입장에서는 둘 다 "이 파일이 행이 됐는가"이기 때문이다.

원본 문서 중 **PDF 는 항목을 추출한다**(추가 요건). 제공 문서가 전부 인쇄 산출물이라 텍스트 레이어가
온전해서, PDFBox 로 글자를 뽑아 그 텍스트만 LLM 에 넘긴다 — 문서를 통째로 보내지 않아 토큰·지연이
적다. 한 문서에서 여러 항목이 나오는 게 정상이고, 같은 파일명을 공유하며 문서 안 순번으로 구분한다.
공문 본문에 없는 `문서ID`·`원본유형` 은 시스템이 부여한다 — 비워 두면 추출한 모든 항목이 필수값
누락으로 떨어진다(진짜 출처는 `source_ref` 가 따로 들고 있다).

원본 문서에서 **글자를 꺼내는 방법만 형식별로 다르다**(`DocumentTextExtractor`) — PDF 는 박혀 있는
텍스트 레이어, 이미지는 OCR(Tesseract). 글자만 나오면 그 뒤 항목 해석·채번·적재·검수는 같은 길이다.
이미지는 표가 한 줄로 유지되도록 `--psm 6` 으로 읽고 괘선 잔재를 걷어낸다. 제공된 사진 2장 기준
기울기·어두운 배경은 Tesseract 가 처리해 별도 전처리(deskew·crop)를 두지 않았다.

LLM 추출기는 `ANTHROPIC_API_KEY` 가 있을 때만 켜진다. 없으면 **규칙 기반 표 파서**가 대신 읽는다 —
우측(적용일자·단가·단위)부터 앵커를 맞추고 남은 왼쪽을 품목명·규격으로 가른다(규격은 숫자로 시작하는
첫 토큰부터). 서식이 달라 한 줄도 못 읽으면 원문을 한 행에 담아 넘긴다. 뽑은 값은 어느 쪽이든
관찰값이고 틀릴 수 있다 — 그래서 검수 단계가 있고, 열을 잘못 잡으면 대개 `unit_mismatch`·
`spec_mismatch` 로 드러난다. OCR 이 설치돼 있지 않으면 이미지는 글자를
꺼낼 방법이 없어 행을 만들지 않는다 — 설치를 안 한 것은 파일 문제가 아니므로 실패가 아니다.

**이미지 추출의 한계**: 제공 사진 기준 표 행·단가·날짜는 정확히 읽히지만 문서 머리의 공급사는
서식에 따라 유실되거나 오독된다(`새봄식품`→`HBAS`). 그 경우 값이 비어 필수값 누락으로 검수에
올라오며, 이는 요구사항의 "필드를 만들지 못했으면 빈칸이 아니라 확인 필요로" 지침과 같은 처리다.

처리가 비동기라 응답만으로는 결과를 알 수 없으므로 세션 현황을 **SSE 로 흘린다**
(`IngestionEventStream`). 흐르는 것은 델타가 아니라 그때의 현황 전체라, 받는 쪽은 순서·유실·재연결을
따질 것 없이 화면을 갈아끼우면 된다. 구독 즉시 스냅샷이 한 번 가므로 최초 조회가 따로 필요 없고,
끊겼다 붙어도 그 스냅샷이 곧 최신이라 놓친 이벤트를 메울 장치가 없다. 화면이 보여주는 만큼만
싣기 때문에(올라온 파일들 + 수기 행들) 3만 행짜리 파일이 올라와도 이벤트 크기는 그대로다.

구독자는 **자기가 붙은 인스턴스**에서 일어난 변화만 본다. 다중화하면 인스턴스 간 팬아웃(Redis pub/sub
등)이 필요하다 — 지금은 단일 인스턴스 전제라 넣지 않았다.

구조화는 `StructuredRecords` 이벤트(같은 트랜잭션)로 검수 단계에 인계된다 — structuring 이 계산하고
inspection 이 저장한다.

## 패키지 구조

루트 패키지는 `com.doq`. 도메인 코드는 `com.doq.comfozi` 아래에 **파이프라인 단계별**로 나뉘고,
세 단계는 나란한 형제다 — `ingestion` 은 다른 단계를 전혀 참조하지 않고, `structuring` 과 `inspection` 이
그 결과를 가져다 쓴다.

```
src/main/kotlin/com/doq/
├─ BackendApplication.kt              Spring Boot 진입점
├─ api/                               HealthController — GET /api/ping
├─ common/
│  ├─ config/                         Jackson·Hibernate jsonb 타입·springdoc OpenAPI 설정·
│  │                                  @Async 활성화와 인입 파싱 워커 풀(AsyncConfig)
│  └─ web/                            공통 응답 envelope(ApiResponse: success/data/error)·
│                                     에러 코드(ApiError)·전역 예외 핸들러
└─ comfozi/                          파이프라인 단계별로 나뉘며, 의존은 왼쪽에서 오른쪽으로만 흐른다
   ├─ ingestion/                      ── 인입 ── 파일 업로드·수기 입력 → 원본 행 적재 (매핑 이전 원문)
   │  ├─ api/                         변경(IngestionController)·조회(IngestionReadController) 컨트롤러 + 요청/응답 DTO ·
   │  │                               세션 현황 SSE 팬아웃(IngestionEventStream)
   │  ├─ domain/                      JPA 엔티티(Ingestion·IngestionUpload·IngestionRecord)·
   │  │                               원문 값 홀더(IngestionContent, jsonb)·상태 enum
   │  ├─ repository/                  Spring Data JPA 리포지토리 3종 (IngestionRepositories.kt 한 파일)
   │  ├─ service/                     IngestionService(변경)·IngestionReadService(조회) + 입력 커맨드 타입 ·
   │  │                               업로드 후속 처리(IngestionUploadStored 이벤트 → Listener → 파싱 워커)
   │  ├─ extraction/                  원본 문서(PDF) → 증빙 항목 (PDFBox 텍스트 추출 + LLM 추출기 포트)
   │  └─ support/                     CSV/XLSX 파서·취합 파일 컬럼 스키마(BatchFileColumn)·
   │                                  업로드 파일 분류(매직바이트)·FileStorage 포트와 로컬 구현
   ├─ structuring/                    ── 구조화(기계) ── 원본 행 → 관찰값
   │  ├─ StructuringService(Impl).kt  인입 세션 1건 구조화 오케스트레이션 (매핑→정규화→탐지→이벤트 발행)
   │  ├─ StructuredRecord(s).kt       구조화 결과 항목 / 세션 단위 결과 이벤트 (→ inspection 인계 계약)
   │  ├─ api/                         StructuringController — POST /api/structuring/{ingestionId}
   │  ├─ mapping/                     출처별 원문 → 캐노니컬 MappedRecord
   │  │                               (RecordMapper 전략 + Dispatcher, 수기·취합파일 구현)
   │  ├─ normalization/               ItemNameNormalizer — 원문 품목명 → 정규화 품목명
   │  └─ detection/                   이상 탐지 — AnomalyDetector 포트 + 규칙 기반 구현·규칙(AnomalyRule)·플래그 enum
   └─ inspection/                     ── 검수(사람) ──
      ├─ InspectionIntakeListener.kt  StructuredRecords 이벤트 수신 → 검수 인박스 영속(저장만)
      ├─ api/                         조회·검수(편집/확정/반려/초기화)·export 컨트롤러 + DTO,
      │                               export 행 스키마(ExportRow)와 CSV writer
      ├─ domain/                      Inspection·InspectionRecord·변경 이력(InspectionChangeLog) 엔티티, 상태 enum
      ├─ repository/                  Spring Data JPA 리포지토리 3종
      └─ service/                     InspectionReviewService(편집·확정·반려·초기화)·InspectionExportService (+ Impl)
```

읽는 순서를 하나 고르자면 `StructuringServiceImpl` → `mapping`/`normalization`/`detection` →
`InspectionIntakeListener` → `inspection/service` 가 파이프라인 순서와 같다.

리소스는 `src/main/resources/` 에 `application.yml` 과 Flyway 마이그레이션(`db/migration/V*.sql`) 이 있고,
테스트는 `src/test/kotlin/` 아래 **동일한 패키지 구조**를 따른다.

## 빌드 & 테스트

```bash
./gradlew build       # 컴파일 + 테스트
./gradlew test        # 테스트만 (H2 in-memory, Docker 불필요)
```

- 테스트 입력: `src/test/resources/fixtures/` — `golden-20.csv`(제공 증빙 20건)로 업로드→구조화→승인
  →export 전 경로를, `notice-*.pdf`·`notice-*.png`(제공 원본 공문 4개)로 텍스트 추출과 표 파싱을 검증한다.
- **OCR 테스트는 Tesseract 가 없으면 건너뛴다** — 나머지는 그대로 통과한다.
- LLM 추출은 추출기를 페이크로 대체해 검증하므로 **테스트에 API 키가 필요 없다.**

## 기술 스택

| 항목 | 버전 |
|---|---|
| Language | Kotlin 2.1 |
| Runtime | Java 21 (LTS, toolchain) |
| Framework | Spring Boot 3.5.x (Web MVC) |
| Build | Gradle 9.4 (Kotlin DSL) |
| RDB | PostgreSQL 16 + Spring Data JPA + Flyway |
| 파일 파싱 | Apache Commons CSV · Apache POI (XLSX) · PDFBox (PDF 텍스트) |
| 이미지 OCR | Tesseract (선택 — 없으면 이미지는 보관만) |
| PDF 항목 추출 | Anthropic Claude Haiku (선택 — `ANTHROPIC_API_KEY` 있을 때만, 모델은 `ANTHROPIC_MODEL` 로 교체) |
| API 문서 | springdoc OpenAPI (Swagger UI) |
| Test DB | H2 (in-memory) |
