# 1. Spring Boot JAR를 만드는 빌드 단계
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /build

# Gradle Wrapper와 빌드 설정 복사
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

# Windows 줄바꿈을 정리하고 Linux 실행 권한 부여
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 애플리케이션 소스 복사
COPY src ./src

# 실행 가능한 Spring Boot JAR 생성
RUN ./gradlew --no-daemon bootJar


# 2. 빌드한 JAR를 실행하는 단계
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# PDF 출력에 필요한 한글 Font 설치
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-nanum \
    && rm -rf /var/lib/apt/lists/*

# 애플리케이션을 실행할 일반 사용자와 저장 디렉터리 준비
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/storage \
    && chown app:app /app/storage

# 빌드 단계에서 생성한 JAR만 최종 Image로 복사
COPY --from=build --chown=app:app /build/build/libs/*.jar /app/app.jar

# 현재 프로젝트에서 사용하는 환경 변수
ENV PDF_FONT_PATH=/usr/share/fonts/truetype/nanum/NanumGothic.ttf
ENV STORAGE_ROOT=/app/storage

USER app

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]