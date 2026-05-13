# Kotlin/JPA + Java/MyBatis 샘플 공통 아키텍처 정렬 설계

## 개요

이 문서는 아래 두 샘플 레포를 “같은 아키텍처 규약을 서로 다른 영속성 기술로 구현한 예제”로 수렴시키기 위한 설계를 정리한다.

- Kotlin/JPA:
  - `/Users/dongjin/dev/study/kotlin-springboot-sample`
- Java/MyBatis:
  - `/Users/dongjin/dev/study/java-springboot-mybatis-sample`

이번 정렬의 목적은 구현 기술을 억지로 통일하는 것이 아니라, 클린 아키텍처 경계와 운영 공통 관심사를 같은 언어로 맞추는 데 있다.

## 배경

현재 두 레포는 이미 `presentation / application / domain / infrastructure` 구조를 공유하고 있지만, 실제 내부 규약은 서로 다르다.

- Kotlin/JPA 샘플
  - `user` 도메인 명칭을 사용한다.
  - JPA aggregate/entity 중심의 상태 변경 흐름이 강하다.
  - `auth`와 JWT 흐름이 이미 존재한다.
  - 공통 예외 추상화와 MDC 기반 요청 추적은 아직 약하다.
- Java/MyBatis 샘플
  - `account` 도메인 명칭을 사용한다.
  - domain service, lookup service, mapper adapter, MDC, trace, 공통 에러 응답이 정리되어 있다.
  - `auth`와 JWT 흐름은 없다.

이 차이 때문에 두 레포를 나란히 볼 때 “패키지는 비슷하지만 학습 포인트는 다른 예제”처럼 보인다. 이번 변경은 이를 “동일한 아키텍처 규약 아래에서 JPA와 MyBatis가 각각 어떤 식으로 구현되는지 비교 가능한 예제”로 정렬하는 작업이다.

## 목표

### 1. 공통 규약 목표

- 도메인 명칭을 `user`로 통일한다.
- `auth` 흐름을 두 레포 모두에서 동일한 책임 분할로 구성한다.
- `UseCase -> command/result -> response` 흐름을 맞춘다.
- 공통 예외 추상화, 에러 응답 포맷, MDC 기반 trace/request 추적을 두 레포 모두에서 지원한다.
- 문서와 테스트 스타일도 공통 규약을 반영한다.

### 2. 기술별 차이 존중 목표

- Kotlin/JPA는 aggregate/entity 메서드 중심 상태 변경을 유지한다.
- Java/MyBatis는 repository port + mapper adapter + 명시적 query/update 흐름을 유지한다.
- domain service는 “필요한 곳에만” 도입한다.
  - MyBatis 쪽은 유지한다.
  - JPA 쪽도 `domain/{도메인}/service` 패키지를 기본 구조에 포함한다.
  - 다만 JPA 쪽은 엔티티/정책으로 흡수 가능한 로직까지 억지로 domain service로 분리하지 않는다.

## 비목표

- 두 레포의 API 스펙을 완전히 동일한 비즈니스 범위로 만드는 것
- 모든 패키지/클래스 수를 1:1로 맞추는 것
- JPA 쪽을 MyBatis 스타일로 바꾸거나, MyBatis 쪽을 JPA 스타일로 바꾸는 것
- 보안, 로깅, 테스트 프레임워크를 같은 라이브러리로 통일하는 것

## 공통 아키텍처 규약

### 1. 계층 책임

#### `presentation`

- HTTP request/response 처리만 담당한다.
- request DTO를 `application.command`로 변환한다.
- `application.result`를 response DTO로 변환한다.
- 예외를 직접 잡아서 비즈니스 분기하지 않는다.
- 공통 성공/실패 응답 포맷은 여기서 결정한다.

#### `application`

- 클래스명은 `*UseCase`를 사용한다.
- 유스케이스 흐름 조합과 트랜잭션 경계를 담당한다.
- 입력은 `command`, 출력은 `result`로 표준화한다.
- 세부 정책 판단, 중복 확인 규칙, 상태 전이 규칙을 직접 구현하지 않는다.
- 필요한 경우 domain service나 repository port를 조합한다.

#### `domain`

- 도메인 모델, 값 객체, 정책, 도메인 서비스, 저장소 포트를 둔다.
- 도메인 의미를 갖는 예외를 정의한다.
- `application.command`, `application.result`, `presentation` DTO를 import 하지 않는다.
- HTTP와 framework API를 모른다.

#### `infrastructure`

- framework/security/logging/persistence adapter를 둔다.
- JPA repository 구현체, MyBatis mapper, security filter/provider, async executor, logging filter, bootstrap이 여기에 속한다.
- domain port 구현 책임을 가진다.

### 2. 의존 방향

허용 방향:

- `presentation -> application`
- `application -> domain`
- `infrastructure -> domain`
- `infrastructure -> framework`
- `config -> infrastructure/application`

금지 방향:

- `domain -> application`
- `domain -> presentation`
- `domain -> infrastructure`
- `application -> presentation`

### 3. 도메인 명명 규칙

- `account`는 전부 `user`로 통일한다.
- Java/MyBatis 레포의 패키지, 클래스명, request/response, 예외명, 문서, 테스트, API path를 모두 `user` 기준으로 바꾼다.
- 외부 API 경로는 `user` 리소스에 대해 `/api/users`를 사용한다.
- 로그인 경로는 두 레포 모두 `POST /api/auth/login`으로 통일한다.

## 공통 운영 관심사 규약

### 1. MDC / Trace

두 레포 모두 아래를 공통 규약으로 둔다.

- 요청 시작 시 `traceId`, `requestId`, `httpMethod`, `requestUri`, `clientIp`를 MDC에 저장한다.
- 응답 헤더에 `X-Trace-Id`, `X-Request-Id`를 반영한다.
- 요청 종료 시 처리 시간과 status를 로그에 남긴다.
- 예외 응답 본문에도 `traceId`를 넣는다.
- Spring async executor를 사용할 때 MDC가 전파되도록 `TaskDecorator`를 둔다.

#### Kotlin/JPA 적용

- Java 샘플의 `TraceContext`, `MdcLoggingFilter`, `MdcTaskDecorator` 패턴을 Kotlin으로 이식한다.
- Kotlin 레포에도 `logback-spring.xml`을 추가해 콘솔 로그 패턴에 trace 정보를 포함한다.
- 보안 필터와 예외 처리 흐름이 MDC를 깨지 않도록 순서를 맞춘다.

#### Java/MyBatis 적용

- 현재 구조를 유지하되 패키지와 문서를 `user/auth` 구조와 충돌 없게 정리한다.
- auth/JWT 추가 이후에도 login 요청과 protected API 요청이 동일한 trace 규약을 따르도록 보완한다.

### 2. 에러 응답

두 레포 모두 “도메인 의미 예외 + presentation 변환” 구조를 따른다.

공통 목표:

- 공통 베이스 예외 추상화로 `common.error.BusinessException` 제공
- 실패 응답 body 필드는 `status`, `code`, `message`, `path`, `traceId`, `timestamp`, `errors`로 통일
- validation / type mismatch / unreadable body / business exception / unexpected exception을 공통 처리

#### Kotlin/JPA 적용

- 현재 `ApiResult` 성공 포맷은 유지하되, 실패 포맷은 Java 샘플과 같은 의미 필드 집합(`status`, `code`, `message`, `path`, `traceId`, `timestamp`, `errors`)을 갖도록 확장한다.
- `UserException`, `OrderNotFoundException` 등 도메인 예외가 `BusinessException`을 상속하도록 정리한다.

#### Java/MyBatis 적용

- 현재 `BusinessException` + `ApiErrorResponse` 구조를 유지한다.
- `account` 기반 예외명을 `user` 기준으로 정리한다.
- auth 예외도 같은 응답 구조로 수렴시킨다.

### 3. Auth / JWT

두 레포 모두 실제 로그인/JWT 기반 인증 흐름을 가진다.

공통 책임 분할:

- `presentation/auth`
  - `POST /api/auth/login` endpoint
  - 로그인 request/response DTO
- `presentation/user`
  - 회원가입 또는 사용자 생성 endpoint는 `POST /api/users`
- `application/auth`
  - `LoginCommand`, `LoginResult`
  - `AuthUseCase`가 로그인 유스케이스를 수행
- `domain/user`
  - 사용자 인증에 필요한 도메인 정보
  - 저장소 포트
- `infrastructure/security`
  - `AuthenticationManager` 연동
  - JWT 발급/검증
  - 토큰 인증 필터
  - `SecurityConfig`

공통 흐름:

1. `AuthController`가 로그인 request를 받아 `LoginCommand`로 변환한다.
2. `AuthUseCase`가 `AuthenticationManager`를 통해 username/password를 검증한다.
3. 인증 성공 시 `JwtTokenProvider`가 access token을 발급한다.
4. 응답 헤더 `Authorization`과 응답 body에 같은 access token 정보를 담는다.
5. 이후 토큰 인증 필터가 JWT를 읽어 `SecurityContext`를 채운다.

공통 로그인 성공 응답:

- `username`
- `tokenType`
- `accessToken`
- `accessTokenExpiresAt`

이번 정렬 범위에서는 refresh token은 도입하지 않는다.

#### Kotlin/JPA 적용

- 현재 필터 기반 로그인 흐름을 controller/usecase 기반 로그인 흐름으로 재정렬한다.
- `AuthUseCase`는 `LoginCommand -> LoginResult`를 수행하는 application service로 둔다.
- `UserDetailsService` 구현은 `infrastructure.security`로 이동시키고, application 계층은 Spring Security 인터페이스를 직접 구현하지 않는다.

#### Java/MyBatis 적용

- 현재 없는 auth/JWT 흐름을 새로 추가한다.
- `user` 저장소 포트를 기반으로 사용자 조회 및 password 검증 흐름을 붙인다.
- `security` 패키지, JWT provider/filter/config, login request/response, 인증 실패 처리 규약을 도입한다.
- MyBatis 사용자 조회 mapper가 auth 흐름에서 재사용되도록 설계한다.
- Kotlin과 동일하게 controller/usecase 기반 로그인 endpoint를 제공한다.

## 패키지 구조 목표

### Kotlin/JPA 최종 목표

```text
src/main/kotlin/com/example/kotlinspringbootsample
├── application
│   ├── auth
│   │   ├── command
│   │   ├── result
│   │   └── AuthUseCase.kt
│   ├── order
│   │   ├── command
│   │   ├── result
│   │   └── OrderUseCase.kt
│   └── user
│       ├── command
│       ├── result
│       └── UserUseCase.kt
├── common
│   ├── error
│   └── logging
├── config
│   ├── async
│   └── security
├── domain
│   ├── order
│   │   ├── exception
│   │   ├── policy
│   │   ├── repository
│   │   └── service
│   └── user
│       ├── exception
│       ├── policy
│       ├── repository
│       └── service
├── infrastructure
│   ├── bootstrap
│   └── security
└── presentation
    ├── auth
    │   ├── request
    │   └── response
    ├── common
    ├── order
    │   ├── request
    │   └── response
    └── user
        ├── request
        └── response
```

핵심 변화:

- `common.error`, `common.logging` 도입
- `config.async` 도입
- `domain.order.service`, `domain.user.service` 패키지 도입
- `application.auth.command/result` 구조 추가
- 필터 기반 로그인 대신 controller/usecase 기반 로그인으로 전환
- 기존 `POST /signup` endpoint를 `POST /api/users`로 이동
- auth/security 응답과 예외 처리를 공통 규약에 맞게 정리

### Java/MyBatis 최종 목표

```text
src/main/java/org/example/javaspringbootmybatissample
├── application
│   ├── auth
│   │   ├── command
│   │   ├── result
│   │   └── AuthUseCase.java
│   ├── order
│   │   ├── command
│   │   ├── result
│   │   ├── OrderUseCase.java
│   │   └── OrderBatchUseCase.java
│   └── user
│       ├── command
│       ├── result
│       └── UserUseCase.java
├── common
│   ├── error
│   ├── logging
│   └── search
├── config
│   ├── async
│   ├── logging
│   ├── mybatis
│   └── security
├── domain
│   ├── order
│   │   ├── exception
│   │   ├── policy
│   │   ├── repository
│   │   └── service
│   └── user
│       ├── exception
│       ├── policy
│       ├── repository
│       └── service
├── infrastructure
│   ├── mybatis
│   ├── order
│   ├── security
│   └── user
└── presentation
    ├── auth
    │   ├── request
    │   └── response
    ├── common
    ├── order
    │   ├── request
    │   └── response
    └── user
        ├── request
        └── response
```

핵심 변화:

- `account` 전 영역을 `user`로 리네이밍
- `auth` 패키지 추가
- `config.security`, `infrastructure.security` 추가
- controller, request/response, service, repository, mapper, 문서까지 `user` 기준으로 정렬

## UseCase 흐름 정렬 원칙

### 공통 원칙

- controller는 request를 command로 바꾸고 use case를 호출한다.
- use case는 트랜잭션 경계 안에서 필요한 domain collaborator를 조합한다.
- use case는 domain model을 result DTO로 변환한다.
- response DTO는 presentation에서만 만든다.

### Kotlin/JPA 쪽 적용 원칙

- aggregate 상태 변경은 엔티티 메서드와 policy 중심으로 유지한다.
- repository는 Spring Data JPA port를 유지한다.
- `domain/{도메인}/service` 패키지를 기본 구조로 두고, repository를 사용하는 도메인 행위는 우선 이 계층에 배치할지 검토한다.
- 조회 보장, 중복 검사, aggregate 생성 보조처럼 repository를 수반하는 도메인 규칙은 domain service로 내린다.
- 다만 상태 전이 자체와 aggregate 내부 불변식은 계속 엔티티 메서드와 policy에 둔다.

### Java/MyBatis 쪽 적용 원칙

- lookup service, domain service, append service 구조를 유지한다.
- repository port는 domain에 두고 mapper adapter가 구현한다.
- optimistic update나 batch 처리처럼 MyBatis가 잘 드러나는 흐름은 유지한다.

## 레포별 상세 설계

### A. Kotlin/JPA 레포 변경 설계

#### 1. 공통 예외/에러 응답 정리

- `common.error` 패키지 도입
- Java 레포와 같은 의미의 `BusinessException` 도입
- `ApiResult.Failure`를 `status`, `code`, `message`, `path`, `traceId`, `timestamp`, `errors` 필드를 가진 구조로 확장
- `GlobalExceptionHandler`가 validation/business/auth/unexpected 예외를 일관 처리

#### 2. MDC / Async

- `TraceContext` 추가
- `MdcLoggingFilter` 추가
- `MdcTaskDecorator`, `AsyncConfiguration` 추가
- 비동기 실행 지점이 현재 적더라도 규약을 먼저 맞춘다

#### 3. Auth 경계 재정리

- `application.auth`에 `LoginCommand`, `LoginResult`, `AuthUseCase`를 추가
- `presentation.auth.AuthController`를 추가하고 로그인 endpoint를 `POST /api/auth/login`으로 고정
- `presentation.user.UserController`의 회원가입 endpoint를 `POST /api/users`로 이동
- `infrastructure.security`의 token/filter/provider 역할을 명확히 분리하고 naming을 `JwtTokenProvider`, `JwtAuthenticationFilter` 기준으로 정리
- 인증 실패와 JWT 예외를 공통 에러 응답으로 연결
- 로그인 성공 응답도 trace와 일관된 API 규약을 따르도록 정리

#### 4. UseCase 다듬기

- `OrderUseCase`와 `UserUseCase`에서 “흐름 조합”에 해당하지 않는 세부 책임이 많은지 점검
- JPA에서 자연스러운 범위 내에서만 분리
- 최소 도입 대상은 아래와 같이 잡는다.
  - `domain.order.service.OrderLookupService`
    - 주문 ID 유효성 확인
    - 주문 조회 보장
  - `domain.user.service.UserRegistrationService`
    - username 중복 확인
    - 비즈니스 관점의 사용자 등록 규칙 조합
  - `domain.user.service.UserLookupService`
    - username 기반 사용자 조회 보장
- `AuthUseCase`는 로그인 유스케이스만 담당하고, Spring Security adapter 책임은 `infrastructure.security`로 고정 분리

#### 5. Domain Service 배치 기준

- `service`로 가는 것
  - repository를 사용하는 도메인 규칙
  - 여러 도메인 객체 또는 aggregate를 엮는 규칙
  - 조회 실패를 도메인 예외로 승격하는 lookup 책임
- `policy`에 남는 것
  - 순수 검증 규칙
  - 외부 저장소 접근 없이 입력값/상태만으로 판단 가능한 규칙
- 엔티티에 남는 것
  - 상태 전이
  - aggregate 내부 컬렉션/합계/불변식 관리

#### 6. 테스트 정렬

- Kotest는 application/domain 테스트에 `BehaviorSpec`, web-layer 테스트에 `DescribeSpec`을 사용하고 `given / when / then` 서술을 유지
- presentation, application, domain, logging/auth 테스트를 레이어별로 정리
- MDC propagation, auth success/failure, error response traceId 노출 테스트 추가
- 새 domain service는 각각 단위 테스트를 추가한다.

### B. Java/MyBatis 레포 변경 설계

#### 1. `account` -> `user` 전환

- 패키지, 클래스, DTO, 예외, 서비스, 레포지토리, mapper 이름을 `user`로 변경
- API path와 Swagger 문서도 `user` 기준으로 변경
- 테스트 class/method/fixture도 같은 기준으로 정리

#### 2. Auth / JWT 추가

- `presentation.auth`
  - `LoginRequest`, `LoginResponse`, `AuthController`
- `application.auth`
  - `LoginCommand`, `LoginResult`, `AuthUseCase`
- `domain.user`
  - auth에 필요한 repository/service
- `config.security`
  - `SecurityConfig`
- 로그인 endpoint는 `POST /api/auth/login`
- 공개 endpoint는 `POST /api/auth/login`, `POST /api/users`, Swagger/H2 관련 path로 제한
- `GET/PUT/DELETE /api/users/**`, `/api/orders/**`는 인증 대상으로 둔다.
- `infrastructure.security`
  - JWT provider, auth filter, authentication provider/manager

#### 3. 에러 응답 확장

- 현재 `ApiErrorResponse`를 유지하되 auth 예외도 같은 규약에 연결
- 인증/인가 실패 시 code/message/path/traceId가 일관되게 내려오도록 처리

#### 4. 테스트 정렬

- JUnit 5는 `@Nested`, `@DisplayName`, `BDDMockito`를 사용해 `given / when / then` 의도를 드러낸다.
- web-layer, domain, mybatis-slice, integration-test 구조는 유지
- 로그인, 토큰 검증, 인증 실패, trace header, error traceId 테스트 추가

## 문서 정렬 범위

두 레포 모두 아래 문서를 동기화한다.

- `README.md`
- `docs/project-structure.md`
- `docs/architecture-boundaries.md`
- `docs/error-handling.md`
- `docs/development-guide.md`
- 보안/로깅 관련 문서
- Kotlin 레포에는 `docs/logging-and-tracing.md`를 새로 추가한다.
  - Java 레포는 기존 문서 갱신

문서에서 강조할 메시지:

- 두 레포는 같은 아키텍처 규약을 공유한다.
- 차이는 영속성 기술에서 오는 구현 방식에 있다.
- `user`, `order`, `auth`, `logging/error`가 공통 학습 축이다.

## 마이그레이션 순서

### 1단계: 공통 언어 정렬

- Java 레포 `account`를 `user`로 리네이밍
- 문서와 API path 정리

### 2단계: 운영 공통 관심사 정렬

- Kotlin 레포에 MDC/trace/async/error abstraction 보강
- Java 레포 auth/JWT 추가

### 3단계: use case / auth 구조 정렬

- auth application/security 책임 분리
- user/order use case 흐름 점검 및 명시된 책임 분리에 맞는 정리

### 4단계: 테스트와 문서 정렬

- given/when/then 스타일 보정
- trace/auth/error 관련 테스트 추가
- 공통 아키텍처 문서 동기화

## 리스크와 대응

### 리스크 1. 리네이밍 범위 과대

- Java 레포의 `account`는 패키지, 클래스, 테스트, 문서, path에 넓게 퍼져 있다.
- 대응:
  - 패키지 rename 이후 compile/test 단위로 잘라서 확인한다.

### 리스크 2. Kotlin auth 책임 중첩

- 현재 `AuthUseCase`는 application use case라기보다 security integration 성격이 강하다.
- 대응:
  - application 책임과 security adapter 책임을 분리하는 기준을 먼저 적용한다.

### 리스크 3. 예외 응답 포맷 변경에 따른 테스트 깨짐

- Kotlin 레포는 에러 응답 구조가 바뀌면 controller test와 문서가 함께 깨진다.
- 대응:
  - 에러 응답 contract를 먼저 확정한 뒤 테스트를 일괄 수정한다.

### 리스크 4. Security 도입에 따른 Java API 접근 제어 영향

- Java 레포에 JWT/security가 들어오면 기존 공개 API 테스트가 깨질 수 있다.
- 대응:
  - permitAll 대상과 protected 대상 범위를 먼저 명시한다.
  - 로그인과 공개 문서 path는 화이트리스트로 분리한다.

## 승인 후 구현 원칙

- 두 레포를 병렬로 건드리더라도 규약은 하나의 기준 문서로 관리한다.
- 변경은 “컴파일 가능 + 테스트 가능” 상태를 유지하는 작은 단위로 나눈다.
- 먼저 naming과 공통 infra를 맞추고, 그 다음 auth와 use case를 정렬한다.
- 테스트는 각 언어/프레임워크 관용구를 유지하되 서술 구조는 맞춘다.

## 요약

이번 설계는 두 샘플을 아래 형태로 수렴시키는 것을 목표로 한다.

- 같은 도메인 언어:
  - `user`, `order`, `auth`
- 같은 경계 규약:
  - `presentation -> application -> domain -> infrastructure`
- 같은 운영 규약:
  - `traceId`, `requestId`, 공통 에러 응답, JWT 기반 auth
- 다른 구현 디테일:
  - Kotlin/JPA는 aggregate/entity 중심
  - Java/MyBatis는 mapper/domain service 중심

이렇게 정리하면 두 레포는 단순히 비슷한 샘플이 아니라, “동일한 설계를 서로 다른 persistence 모델로 비교 학습할 수 있는 페어 예제”가 된다.
