# 런타임 전용 이미지 — jar 는 호스트에서 ./gradlew bootJar 로 만든 뒤 복사만 한다.
# jar 는 아키텍처 독립이므로 arm64 맥에서도 linux/amd64 이미지를 즉시 만들 수 있다.
FROM eclipse-temurin:21-jre

# curl: compose healthcheck 용 (temurin 이미지엔 curl/wget 둘 다 없음)
# tesseract-ocr(+kor): 이미지 원본에서 글자를 뽑는 데 쓴다. 없으면 이미지는 보관만 되고
#   부팅·업로드는 정상이므로, 이미지 입력을 안 쓸 거면 이 두 패키지는 빼도 된다(~40MB).
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata tesseract-ocr tesseract-ocr-kor \
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
