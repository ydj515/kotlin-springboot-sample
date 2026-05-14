# 프로젝트 구조 가이드

## 개요

이 프로젝트는 계층별 상위 패키지와 도메인별 하위 패키지를 함께 사용합니다.

## 이 문서를 보면 좋은 경우

- 패키지 구조와 디렉토리 배치를 빠르게 이해하고 싶을 때
- `Request/Response`, `Command/Result`, `UseCase`, `Policy`가 어디에 위치하는지 보고 싶을 때
- 새 도메인을 추가할 때 어떤 위치에 파일을 두어야 할지 알고 싶을 때

## 핵심 내용

### Kotlin 소스 구조

- `src/main/kotlin/.../presentation`
  - API 계층입니다.
  - `controller`, `request`, `response` DTO를 둡니다.
- `src/main/kotlin/.../application`
  - 유스케이스 계층입니다.
  - `*UseCase`, `command`, `result`, 매핑 헬퍼를 둡니다.
- `src/main/kotlin/.../domain`
  - 도메인 모델 계층입니다.
  - 엔티티, 정책, 도메인 서비스, 예외, 저장소 포트를 둡니다.
- `src/main/kotlin/.../infrastructure`
  - JWT, 인증 어댑터, filter, bootstrap seed 같은 외부 기술 연동 코드를 둡니다.
- `src/main/kotlin/.../config`
  - Spring/JPA/Swagger 설정을 둡니다.
- `src/main/kotlin/.../common`
  - `BaseEntity`, 공통 예외, 로깅/트레이싱 키 같은 공통 타입을 둡니다.

### 현재 도메인 예시

- `presentation/user`, `application/user`, `domain/user`
  - 회원가입과 등록 정책 예제
- `presentation/auth`, `application/auth`, `infrastructure/security`
  - `POST /api/auth/login`, JWT 발급/검증 흐름 예제
- `presentation/order`, `application/order`, `domain/order`
  - 주문 aggregate와 연관관계 매핑 예제

### 계층 간 DTO 규칙

- 컨트롤러 계층:
  - `xxRequest`, `xxResponse`
- 애플리케이션 계층:
  - `xxCommand`, `xxResult`
- 애플리케이션 클래스:
  - `xxUseCase`
- 도메인 계층:
  - 엔티티, 정책, 서비스는 controller DTO를 직접 참조하지 않습니다.

### 도메인 서비스 예시

- `domain/user/service/UserRegistrationService`
  - username 중복 확인과 저장소 연동
- `domain/user/service/UserLookupService`
  - username 기반 사용자 조회 보장
- `domain/order/service/OrderLookupService`
  - 주문 조회 보장과 not-found 예외 변환

### 테스트 구조

- `src/test/kotlin/.../presentation`
  - `@WebMvcTest`
- `src/test/kotlin/.../application`
  - MockK 기반 유스케이스 테스트
- `src/test/kotlin/.../domain`
  - `@DataJpaTest` 또는 순수 단위 테스트
- `src/test/resources/application-test.yml`
  - 테스트 전용 설정

## 관련 문서

- [아키텍처 경계 원칙](./architecture-boundaries.md)
- [로깅 및 트레이싱 가이드](./logging-and-tracing.md)
- [JPA 샘플 개요](./jpa-sample-overview.md)
