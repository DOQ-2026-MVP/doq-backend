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
같은 상태 어휘를 쓰는데, 화면 입장에서는 둘 다 "이 파일이 행이 됐는가"이기 때문이다 — 원본 문서는
행 자동 추출을 지원하지 않아 행 0건으로 `PARSED` 가 된다(추출이 붙을 자리는
`IngestionUploadStoredListener` 의 문서 분기).

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

루트 패키지는 `com.doq`. 도메인 코드는 `com.doq.comfozi` 아래에 **파이프라인 단계별**로 나뉜다.

```
src/main/kotlin/com/doq/
├─ BackendApplication.kt              Spring Boot 진입점
├─ api/                               HealthController — GET /api/ping
├─ common/
│  ├─ config/                         Jackson·Hibernate jsonb 타입·springdoc OpenAPI 설정·
│  │                                  @Async 활성화와 인입 파싱 워커 풀(AsyncConfig)
│  └─ web/                            공통 응답 envelope(ApiResponse: success/data/error)·
│                                     에러 코드(ApiError)·전역 예외 핸들러
└─ comfozi/
   ├─ structuring/                    ── 기계 단계 ──
   │  ├─ StructuringService(Impl).kt  인입 세션 1건 구조화 오케스트레이션 (매핑→정규화→탐지→이벤트 발행)
   │  ├─ StructuredRecord(s).kt       구조화 결과 항목 / 세션 단위 결과 이벤트 (→ inspection 인계 계약)
   │  ├─ api/                         StructuringController — POST /api/structuring/{ingestionId}
   │  ├─ ingestion/                   인입: 파일 업로드·수기 입력 → 원본 행 적재 (매핑 이전 원문)
   │  │  ├─ api/                      변경(IngestionController)·조회(IngestionReadController) 컨트롤러 + 요청/응답 DTO ·
   │  │  │                            세션 현황 SSE 팬아웃(IngestionEventStream)
   │  │  ├─ domain/                   JPA 엔티티(Ingestion·IngestionUpload·IngestionRecord)·
   │  │  │                            원문 값 홀더(IngestionContent, jsonb)·상태 enum
   │  │  ├─ repository/               Spring Data JPA 리포지토리 3종 (IngestionRepositories.kt 한 파일)
   │  │  ├─ service/                  IngestionService(변경)·IngestionReadService(조회) + 입력 커맨드 타입 ·
   │  │  │                            업로드 후속 처리(IngestionUploadStored 이벤트 → Listener → 파싱 워커)
   │  │  └─ support/                  CSV/XLSX 파서·취합 파일 컬럼 스키마(BatchFileColumn)·
   │  │                               원본 문서 매직바이트 검증·FileStorage 포트와 로컬 구현
   │  ├─ mapping/                     출처별 원문 → 캐노니컬 MappedRecord
   │  │                               (RecordMapper 전략 + Dispatcher, 수기·취합파일 구현)
   │  ├─ normalization/               ItemNameNormalizer — 원문 품목명 → 정규화 품목명
   │  └─ detection/                   이상 탐지 — AnomalyDetector 포트 + 규칙 기반 구현·규칙(AnomalyRule)·플래그 enum
   └─ inspection/                     ── 사람 단계 ──
      ├─ InspectionIntakeListener.kt  StructuredRecords 이벤트 수신 → 검수 인박스 영속(저장만)
      ├─ api/                         조회·검수(편집/확정/반려)·export 컨트롤러 + DTO,
      │                               export 행 스키마(ExportRow)와 CSV writer
      ├─ domain/                      Inspection·InspectionRecord·변경 이력(InspectionChangeLog) 엔티티, 상태 enum
      ├─ repository/                  Spring Data JPA 리포지토리 3종
      └─ service/                     InspectionReviewService(편집·확정·반려)·InspectionExportService (+ Impl)
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

- 테스트 입력: `src/test/resources/fixtures/golden-20.csv` (제공 증빙 20건). 업로드→구조화→승인→export
  전 경로를 실제 파일로 검증한다.

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
