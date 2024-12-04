# 실행할 기본 이미지 설정
FROM openjdk:17-jdk-slim

# 작업 디렉토리 설정
WORKDIR /app

# CI에서 다운로드한 JAR 파일을 Docker 컨텍스트에서 이미지로 복사
COPY build/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 5. 애플리케이션 실행 명령어
CMD ["java", "-jar", "kotlin-springboot-sample.jar"]
