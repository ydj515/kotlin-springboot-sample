# Gradle 빌드 이미지 사용
FROM gradle:8.10-jdk17 AS builder

# 작업 디렉토리 설정
WORKDIR /app

# gradle 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY src src

# 권한 부여
RUN chmod +x ./gradlew

# jar 생성
RUN ./gradlew bootJar --no-daemon

# 빌드
RUN chmod +x ./gradlew
RUN ./gradlew build

# 실행할 기본 이미지 설정
FROM openjdk:17-jdk-slim

# 애플리케이션 JAR 파일을 복사
COPY --from=builder /app/build/libs/*.jar /app/kotlin-springboot-sample.jar

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app/kotlin-springboot-sample.jar"]

# 포트 노출
EXPOSE 8080
