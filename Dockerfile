# 런타임 전용 이미지 — jar 는 호스트에서 ./gradlew bootJar 로 만든 뒤 복사만 한다.
# jar 는 아키텍처 독립이므로 arm64 맥에서도 linux/amd64 이미지를 즉시 만들 수 있다.
FROM eclipse-temurin:21-jre

# curl: compose healthcheck 용 (temurin 이미지엔 curl/wget 둘 다 없음)
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/*

# reviewed_at 이 서버 로컬 시각을 +09:00 으로 표기하므로 TZ 를 KST 로 고정한다
ENV TZ=Asia/Seoul

RUN useradd -r -u 10001 -m doq \
 && mkdir -p /var/lib/doq/uploads \
 && chown -R doq /var/lib/doq

WORKDIR /app
ARG JAR=build/libs/backend-0.0.1-SNAPSHOT.jar
COPY ${JAR} /app/app.jar

USER doq
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
