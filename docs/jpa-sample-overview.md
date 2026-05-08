# JPA 샘플 개요

## 개요

이 프로젝트는 단순 JPA CRUD 예제보다 조금 더 넓은 범위를 보여주는 데 목적이 있습니다.  
현재는 `post`, `user`, `order` 세 도메인을 통해 JPA에서 자주 마주치는 설계 포인트를 샘플 형태로 담고 있습니다.

## 이 문서를 보면 좋은 경우

- 이 저장소가 어떤 JPA 기능을 예제로 담고 있는지 한 번에 보고 싶을 때
- `Order` 도메인이 왜 추가되었는지, 어떤 매핑을 보여주기 위한 것인지 알고 싶을 때
- JPA 학습용으로 어떤 파일부터 보면 좋은지 찾고 싶을 때

## 핵심 예제 포인트

### `Post`

- `deletedAt` 기반 soft delete
- `findAllByDeletedAtIsNull(...)` paging 조회
- dirty checking 기반 수정
- `PostAuthorPolicy`를 통한 작성자 검증

### `User`

- `@Column(unique = true)` 기반 username uniqueness
- 회원가입 흐름의 `command -> usecase -> policy -> repository`
- `UserRegistrationPolicy`를 통한 중복 확인

### `Order`

- `Order -> User`
  - `@ManyToOne(fetch = LAZY)`
- `Order -> OrderLine`
  - `@OneToMany(cascade = ALL, orphanRemoval = true)`
- `ShippingAddress`
  - `@Embeddable`
- `OrderStatus`
  - `@Enumerated(EnumType.STRING)`
- `version`
  - `@Version` 기반 optimistic locking
- `totalAmount`
  - aggregate root가 자식 라인 변경에 맞춰 합계를 유지
- `paidAt`, `shippedAt`, `cancelledAt`
  - audit 컬럼과 별개로 도메인 이벤트 시각을 기록
- 상태 전이
  - `markPaid()`, `markShipped()`, `cancel()`
  - `OrderStatusTransitionPolicy`로 허용 가능한 전이를 검증
- `OrderRepository`
  - `findAllByBuyerUsernameAndDeletedAtIsNull(...)`
  - `findAllByDeletedAtIsNullAndStatus(...)`
  - `findAllByBuyerUsernameAndStatusAndDeletedAtIsNull(...)`
    - status 조건이 포함된 파생 쿼리 예제
  - `@EntityGraph(attributePaths = ["buyer", "orderLines"])`
  - `findDetailByIdUsingFetchJoin(...)`
    - fetch join 기반 상세 조회 예제
  - `searchByConditions(...)`
    - nullable 조건식 기반 JPQL 검색 예제
  - `findStatusSummaries()`
    - `buyerUsername`, `status` 조건을 받는 JPQL `@Query` + interface projection 예제
  - `findPageWithoutBuyer(...)`
    - EntityGraph 없는 기본 로딩과 N+1 비교용 예제

## 예시 API

### Post

- `GET /api/posts`
- `GET /api/posts/{id}`
- `POST /api/posts`

### User

- `POST /signup`

### Order

- `GET /api/orders`
  - 예: `/api/orders?status=PAID&searchMode=DERIVED`
  - 예: `/api/orders?buyerUsername=alice&status=PAID&searchMode=JPQL`
- `GET /api/orders/status-summary`
- `GET /api/orders/status-summary?buyerUsername=alice&status=PAID`
- `GET /api/orders/{id}`
- `POST /api/orders`
- `POST /api/orders/{id}/pay`
- `POST /api/orders/{id}/ship`
- `POST /api/orders/{id}/cancel`

## 예시로 보면 좋은 파일

- `domain/post/Post.kt`
- `domain/user/policy/UserRegistrationPolicy.kt`
- `domain/order/Order.kt`
- `domain/order/OrderLine.kt`
- `domain/order/policy/OrderStatusTransitionPolicy.kt`
- `application/order/OrderUseCase.kt`
- `domain/order/repository/OrderRepository.kt`
- `domain/order/repository/OrderRepositoryTest.kt`
- `domain/order/OrderOptimisticLockingIntegrationTest.kt`
- `presentation/order/OrderController.kt`
- `presentation/order/OrderControllerTest.kt`

## 상태 전이 API가 추가되며 새로 볼 수 있는 것

- dirty checking 기반 상태 변경
  - 결제/배송/취소 API는 엔티티를 조회한 뒤 `save()` 없이 상태만 바꾸고 트랜잭션 종료 시 update 됩니다.
- optimistic locking
  - `@Version` 컬럼이 동시에 들어온 상태 전이 요청 충돌을 감지합니다.
- 비즈니스 시각 컬럼
  - `createdAt`, `updatedAt` 같은 공통 audit과 `paidAt`, `shippedAt`, `cancelledAt` 같은 도메인 시각을 구분해서 볼 수 있습니다.
- JPQL 집계 + projection
  - `GET /api/orders/status-summary`는 상태별 주문 수를 projection으로 바로 반환합니다.
  - `buyerUsername`, `status` 조건을 함께 받아 집계 범위를 줄일 수 있습니다.
- 파생 쿼리 vs JPQL 조건식 비교
  - `GET /api/orders`는 `searchMode=DERIVED`와 `searchMode=JPQL`을 바꿔 같은 `buyerUsername`, `status` 조건을 두 방식으로 조회해볼 수 있습니다.
- N+1 비교
  - `OrderRepositoryTest`에서 `findPageWithoutBuyer(...)`, `findAllByDeletedAtIsNull(...)`, `findDetailByIdUsingFetchJoin(...)`를 비교해 로딩 전략 차이를 확인할 수 있습니다.
- fetch join 예제
  - 상세 조회에서 `EntityGraph`와 fetch join이 같은 결과를 만들 수 있지만, 선언 방식과 주의점은 다르다는 점을 볼 수 있습니다.
- optimistic locking 재현 테스트
  - `OrderOptimisticLockingIntegrationTest`에서 오래된 스냅샷 저장 시 `ObjectOptimisticLockingFailureException`이 실제로 발생하는 것을 검증합니다.

## 관련 문서

- [JPA 사용 기준 가이드](./jpa-pattern-selection-guide.md)
- [프로젝트 구조 가이드](./project-structure.md)
