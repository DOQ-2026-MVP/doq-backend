# DOQ Backend

ComfoziAI 구매 증빙 인박스 백엔드입니다. 취합 파일(XLSX/CSV)·수기 입력·원본 공문(PDF·이미지)을
검수 가능한 후보로 구조화하고, 사람이 검수·승인한 항목만 전달용 JSON·CSV 로 export 합니다.

| | |
|---|---|
| 구동 URL | <https://doq.siotman.work> (Basic Auth, 계정 별도 전달) |
| 프론트엔드 | <https://github.com/DOQ-2026-MVP/doq-frontend> |
| 배포 구성 | <https://github.com/DOQ-2026-MVP/doq-deploy> |

전체 스택을 한 번에 띄우려면 배포 구성 저장소를 쓰시면 됩니다. 여기는 백엔드 단독 실행 방법입니다.

## 요구 사항

- JDK 21 이상 (미설치 시 Gradle toolchain 이 자동 다운로드합니다)
- Docker / Docker Compose — 로컬 PostgreSQL 자동 실행에 씁니다

## 실행

```bash
./gradlew bootRun
```

PostgreSQL 컨테이너가 자동으로 뜨고 접속 정보가 주입됩니다. 스키마는 Flyway 로 적용됩니다.

### 선택 의존성

없어도 부팅과 필수 흐름(취합 파일 + 수기 입력)은 전부 동작합니다.

- **Tesseract** — 이미지에서 글자를 뽑는 데 씁니다. 없으면 이미지는 보관만 됩니다.
  Docker 이미지에는 이미 들어 있습니다.
- **`ANTHROPIC_API_KEY`** — 문서에서 뽑은 텍스트를 LLM 이 해석해 항목으로 적재합니다.
  없으면 규칙 기반 표 파서가 대신 읽습니다.

## 테스트

```bash
./gradlew test
```

H2 in-memory 라 Docker 가 필요 없습니다. OCR 테스트는 Tesseract 가 없으면 건너뛰고, LLM 추출은
페이크로 대체하므로 API 키가 필요 없습니다.

## 지원 범위

- 취합 파일(XLSX·CSV) 업로드와 수기 입력 → 원본 행 적재
- 매핑·정규화·이상 탐지를 거친 구조화
- 검수 인박스에서 편집·확정·반려와 변경 이력
- 승인된 항목만 JSON·CSV 로 export
- 원본 공문 PDF 에서 증빙 항목 추출 (텍스트 레이어를 읽어 LLM 이 해석)
- 원본 공문 이미지(PNG·JPEG) OCR 추출

## 미지원

- **단일 인스턴스 전제입니다.** 세션 현황 SSE 는 자기가 붙은 인스턴스의 변화만 봅니다.
  다중화하려면 인스턴스 간 팬아웃이 따로 필요합니다.
- 사용자 계정·권한 구분이 없습니다.

## 알려진 오류 · 한계

- **이미지에서 공급사가 유실되거나 오독됩니다.** 표 행·단가·날짜는 정확히 읽히지만 문서 머리의
  공급사는 서식에 따라 틀립니다(예: `새봄식품` → `HBAS`). 그 경우 값이 비어 필수값 누락으로
  검수에 올라옵니다 — 빈칸으로 두지 않고 확인 필요로 넘기는 의도된 처리입니다.
- **업로드 직후에는 행이 없을 수 있습니다.** 파일 처리는 접수까지만 동기이고 파싱·추출은
  뒤따릅니다. 처리 실패는 예외가 아니라 상태와 사유로 남습니다.

## 기술 스택

| 항목 | 버전 |
|---|---|
| Language | Kotlin 2.1 |
| Runtime | Java 21 (LTS, toolchain) |
| Framework | Spring Boot 3.5.x (Web MVC) |
| Build | Gradle 9.4 (Kotlin DSL) |
| RDB | PostgreSQL 16 + Spring Data JPA + Flyway |
| 파일 파싱 | Apache Commons CSV · Apache POI (XLSX) · PDFBox (PDF 텍스트) |
| 이미지 OCR | Tesseract (선택) |
| PDF 항목 추출 | Anthropic Claude Haiku (선택) |
| Test DB | H2 (in-memory) |
