# kotlin-springboot-sample

Kotlin + Spring Boot + JPA를 학습하기 위한 샘플 프로젝트입니다.  
단순 CRUD만 두지 않고 `presentation / application / domain / infrastructure` 구조, `UseCase / command / result / policy` 분리, 그리고 JPA에서 자주 다루는 연관관계 예제를 함께 담고 있습니다.

## Environment

- Java `21.0.2`
- Spring Boot `3.5.14`
- Kotlin `1.9.25`
- Gradle `8.14.4`
- Database
  - local/test: `H2`

## Run

### mise 사용

```bash
mise install
mise run
mise run test
mise run build
```

### Gradle Wrapper 사용

```bash
./gradlew bootRun
./gradlew test
./gradlew build
```

## Architecture

- `presentation`
  - HTTP 요청/응답 DTO와 controller를 둡니다.
- `application`
  - `*UseCase`, `command`, `result`를 둡니다.
- `domain`
  - 엔티티, 정책(`policy`), 도메인 서비스(`service`), 예외, 저장소 포트를 둡니다.
- `infrastructure`
  - JWT 발급/검증, 인증 어댑터, MDC 추적, bootstrap seed 같은 기술 연동 코드를 둡니다.

현재 샘플 도메인:

- `user`
  - 회원가입, unique username 정책 예제
- `order`
  - `ManyToOne`, `OneToMany`, `Embeddable`, `Enum`, `@Version`, `EntityGraph`, fetch join, JPQL projection 예제

## JPA Examples

- `Order`
  - `Order -> User` : `@ManyToOne(fetch = LAZY)`
  - `Order -> OrderLine` : `@OneToMany(cascade = ALL, orphanRemoval = true)`
  - `ShippingAddress` : `@Embeddable`
  - `OrderStatus` : `@Enumerated(EnumType.STRING)`
  - `version` : `@Version`
  - `paidAt / shippedAt / cancelledAt` : 도메인 이벤트 시각 컬럼
  - `OrderRepository.findAllByBuyerUsernameAndDeletedAtIsNull(...)`
  - `OrderRepository.findAllByDeletedAtIsNullAndStatus(...)`
  - `@EntityGraph(attributePaths = ["buyer", "orderLines"])`
  - `fetch join` 기반 `findDetailByIdUsingFetchJoin(...)`
  - `searchMode=DERIVED | JPQL`로 파생 쿼리와 조건식 JPQL 비교
  - `@Query` + projection 기반 `findStatusSummaries()`

## Sample API

- `POST /api/users`
- `POST /api/auth/login`
- `GET /api/orders`
- `GET /api/orders?status=PAID&searchMode=DERIVED`
- `GET /api/orders?buyerUsername=alice&status=PAID&searchMode=JPQL`
- `GET /api/orders/status-summary`
- `GET /api/orders/status-summary?buyerUsername=alice&status=PAID`
- `GET /api/orders/{id}`
- `POST /api/orders`
- `POST /api/orders/{id}/pay`
- `POST /api/orders/{id}/ship`
- `POST /api/orders/{id}/cancel`

## Docs

- [문서 안내](./docs/README.md)
- [프로젝트 구조 가이드](./docs/project-structure.md)
- [아키텍처 경계 원칙](./docs/architecture-boundaries.md)
- [로깅 및 트레이싱 가이드](./docs/logging-and-tracing.md)
- [에러 처리 가이드](./docs/error-handling.md)
- [JPA 샘플 개요](./docs/jpa-sample-overview.md)
- [JPA 사용 기준 가이드](./docs/jpa-pattern-selection-guide.md)

## Docker build

```bash
docker build --no-cache -t {app-name} .
docker run -p 8080:8080 {app-name}
```
