# DOQ Backend

Spring Boot (MVC) 백엔드 서비스.

## 기술 스택

| 항목        | 버전                         |
| ----------- | ---------------------------- |
| Language    | Kotlin 2.1                   |
| Runtime     | Java 21 (LTS, toolchain)     |
| Framework   | Spring Boot 3.5.x (Web MVC)  |
| Build       | Gradle 9.4 (Kotlin DSL)      |
| Persistence | Spring Data JPA + PostgreSQL |
| Test DB     | H2 (in-memory)               |

## 요구 사항

- JDK 21 이상 (미설치 시 Gradle toolchain 이 foojay 로 자동 다운로드)
- 로컬 실행용 PostgreSQL (또는 아래 환경변수로 접속 정보 지정)

## 빌드 & 테스트

```bash
./gradlew build       # 컴파일 + 테스트
./gradlew test        # 테스트만
./gradlew bootRun     # 앱 실행 (PostgreSQL 필요)
```

## 실행 (환경변수)

`application.yml` 은 아래 환경변수로 DB 접속 정보를 오버라이드합니다.

| 변수          | 기본값                                 |
| ------------- | -------------------------------------- |
| `DB_URL`      | `jdbc:postgresql://localhost:5432/doq` |
| `DB_USERNAME` | `doq`                                  |
| `DB_PASSWORD` | `doq`                                  |

로컬 PostgreSQL 예시:

```bash
docker run --name doq-postgres -e POSTGRES_DB=doq \
  -e POSTGRES_USER=doq -e POSTGRES_PASSWORD=doq \
  -p 5432:5432 -d postgres:16
```
