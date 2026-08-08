# DOQ Backend

Spring Boot (MVC) 백엔드 서비스.

## 요구 사항

- JDK 21 이상 (미설치 시 Gradle toolchain 이 foojay 로 자동 다운로드)
- Docker / Docker Compose (로컬 PostgreSQL 자동 실행에 사용)

## 실행 (자동 런칭)

```bash
./gradlew bootRun     # compose.yaml 의 postgres 를 띄우고 앱 실행
```

- 컨테이너가 `healthy` 가 될 때까지 대기한 뒤 앱이 부팅됩니다.
- 앱을 종료하면 컨테이너도 함께 종료됩니다
  (`spring.docker.compose.lifecycle-management: start-and-stop`).
- 데이터는 named volume(`postgres-data`)에 보존됩니다.

`spring-boot-docker-compose` 지원이 포함되어 있어, 앱을 실행하면 프로젝트 루트의
`compose.yaml` 에 정의된 **PostgreSQL 컨테이너가 자동으로 기동**되고
접속 정보(datasource URL / 계정)가 앱에 자동 주입됩니다.
별도의 DB 설치나 환경변수 설정이 필요 없습니다.

## 실행 확인: Actuator 헬스 체크

```bash
curl -s localhost:8080/actuator/health 
# {"status":"UP"}
```

## 빌드 & 테스트

```bash
./gradlew build       # 컴파일 + 테스트
./gradlew test        # 테스트만 (H2 사용, Docker 불필요)
```

## 접속 정보 (compose 기본값)

| 서비스     | 값                                                     |
| ---------- | ------------------------------------------------------ |
| PostgreSQL | `localhost:5432` / db `doq` / user `doq` / pw `doq`    |

Docker Compose 없이 외부 DB 로 실행할 경우 환경변수로 오버라이드:

| 변수          | 기본값                                                    |
| ------------- | --------------------------------------------------------- |
| `DB_URL`      | `jdbc:postgresql://localhost:5432/doq`                    |
| `DB_USERNAME` | `doq`                                                     |
| `DB_PASSWORD` | `doq`                                                     |

## 기술 스택

| 항목        | 버전                         |
| ----------- | ---------------------------- |
| Language    | Kotlin 2.1                   |
| Runtime     | Java 21 (LTS, toolchain)     |
| Framework   | Spring Boot 3.5.x (Web MVC)  |
| Build       | Gradle 9.4 (Kotlin DSL)      |
| RDB         | PostgreSQL 16 + Spring Data JPA |
| Test DB     | H2 (in-memory)               |
