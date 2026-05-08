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
  - 엔티티, 정책(`policy`), 예외, 저장소 포트를 둡니다.
- `infrastructure`
  - JWT, filter, bootstrap seed 같은 기술 연동 코드를 둡니다.

현재 샘플 도메인:

- `post`
  - soft delete, dirty checking, paging 예제
- `user`
  - 회원가입, unique username 정책 예제
- `order`
  - `ManyToOne`, `OneToMany`, `Embeddable`, `Enum`, `@Version`, `EntityGraph`, fetch join, JPQL projection 예제

## JPA Examples

- `Post`
  - 수정 시 명시적 `save()` 없이 dirty checking으로 반영
  - `deletedAt` 기반 soft delete
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

- `POST /signup`
- `GET /api/posts`
- `GET /api/posts/{id}`
- `POST /api/posts`
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
- [JPA 샘플 개요](./docs/jpa-sample-overview.md)
- [JPA 사용 기준 가이드](./docs/jpa-pattern-selection-guide.md)

## Docker build

```bash
docker build --no-cache -t {app-name} .
docker run -p 8080:8080 {app-name}
```
