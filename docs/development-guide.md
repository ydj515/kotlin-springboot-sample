# 개발 및 실행 가이드

## 개요

이 문서는 로컬 개발 환경을 맞추고 애플리케이션을 실행하는 데 필요한 기본 정보를 정리합니다.

## 이 문서를 보면 좋은 경우

- 처음 저장소를 내려받고 실행 순서를 확인하고 싶을 때
- `mise`와 Gradle Wrapper 중 어떤 명령을 쓰면 되는지 빠르게 보고 싶을 때
- 테스트 프로필과 샘플 설정 파일 구성을 이해하고 싶을 때

## 핵심 내용

### 기술 스택

- Java 21
- Spring Boot 3.5.14
- Kotlin 1.9.25
- Gradle 8.14.4
- Spring Data JPA
- H2

### 개발 환경

- `mise.toml`에 Java 21, Gradle 8.14.4와 주요 task가 정의되어 있습니다.
- 기본 개발 명령은 `mise run`이며 내부적으로 `./gradlew bootRun`을 실행합니다.
- 테스트는 `application-test.yml`을 사용해 H2 메모리 데이터베이스에서 동작합니다.
- 런타임 예시 설정은 `src/main/resources/application-sample.yml`을 참고합니다.
- 애플리케이션 계층은 `*UseCase`, 입력은 `command`, 출력은 `result`, 검증 규칙은 `domain/*/policy`로 분리합니다.

### 자주 쓰는 명령

#### mise 사용

- `mise install`
- `mise run`
- `mise run test`
- `mise run build`
- `mise run jar`
- `mise run check`

#### Gradle Wrapper 사용

- `./gradlew bootRun`
- `./gradlew test`
- `./gradlew build`
- `./gradlew bootJar`

### 테스트 환경

- `@WebMvcTest`
  - controller와 HTTP 응답 계약 검증
- `@DataJpaTest`
  - JPA 매핑, 연관관계, repository 메서드 검증
- `@SpringBootTest`
  - 애플리케이션 컨텍스트와 설정 로딩 검증

### 현재 샘플 도메인

- `post`
  - paging, soft delete, dirty checking
- `user`
  - 회원가입과 unique username 정책
- `order`
  - `ManyToOne`, `OneToMany`, `Embeddable`, `Enum`, `EntityGraph`, 파생 쿼리 메서드

## 관련 문서

- [프로젝트 구조 가이드](./project-structure.md)
- [JPA 샘플 개요](./jpa-sample-overview.md)
