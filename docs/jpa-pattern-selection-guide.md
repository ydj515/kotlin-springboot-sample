# JPA 사용 기준 가이드

## 개요

이 문서는 현재 샘플 프로젝트에서 보여주는 JPA 패턴을 언제 선택하면 좋은지 정리한 실전형 기준서입니다.

## 1. 언제 엔티티 메서드로 두는가

- 자기 상태만으로 수정 가능할 때
- aggregate 내부 불변조건일 때
- 예:
  - `Post.update(...)`
  - `Order.replaceLines(...)`
  - `Order.markPaid()`
  - `Order.markShipped()`
  - `Order.cancel()`

## 2. 언제 `policy`로 분리하는가

- 검증/판단 규칙일 때
- 유스케이스 여러 곳에서 재사용될 수 있을 때
- 저장소 조회가 없어도 판단 가능할 때
- 예:
  - `PostAuthorPolicy`
  - `UserRegistrationPolicy`
  - `OrderItemPolicy`
  - `OrderStatusTransitionPolicy`

## 3. 언제 `ManyToOne`을 쓰는가

- 주문이 구매자를 참조하는 것처럼 “자식이 부모를 바라보는” 연관일 때
- 목록 조회에서 무분별한 eager loading을 피하려면 `LAZY`를 기본으로 둡니다.
- 예:
  - `Order.buyer`

## 4. 언제 `OneToMany + cascade + orphanRemoval`을 쓰는가

- aggregate root가 자식 생명주기를 함께 관리할 때
- 부모가 자식을 추가/교체/삭제하는 흐름이 자연스러울 때
- 예:
  - `Order.orderLines`

주의:

- orphan removal은 “목록에서 빠지면 삭제”되는 의미가 분명할 때만 씁니다.

## 5. 언제 `Embeddable`을 쓰는가

- 주소처럼 엔티티보다 값 객체에 가까운 묶음 데이터일 때
- 여러 컬럼이지만 하나의 의미 단위로 다루고 싶을 때
- 예:
  - `ShippingAddress`

## 6. 언제 `@Version`을 쓰는가

- 같은 주문에 대해 결제/취소 같은 경쟁 업데이트가 들어올 수 있을 때
- 마지막 저장이 이전 상태를 덮어써도 되는 도메인이 아닐 때
- 예:
  - `Order.version`

주의:

- `@Version`은 충돌을 막는 장치이지, 전이 규칙 자체를 대체하지는 않습니다.
- 상태 전이 허용 여부는 `OrderStatusTransitionPolicy`가 맡고, 동시성 충돌 감지는 `@Version`이 맡습니다.

## 7. 언제 dirty checking을 기대하는가

- 영속 상태 엔티티를 트랜잭션 안에서 수정할 때
- save 재호출이 꼭 필요하지 않을 때
- 예:
  - `PostUseCase.updatePost(...)`
  - `PostUseCase.deletePost(...)`
  - `OrderUseCase.payOrder(...)`
  - `OrderUseCase.shipOrder(...)`
  - `OrderUseCase.cancelOrder(...)`

## 8. 언제 `EntityGraph`를 쓰는가

- 상세 조회에서 연관 엔티티를 함께 읽고 싶을 때
- 기본 LAZY 전략은 유지하면서 특정 쿼리만 fetch plan을 조정하고 싶을 때
- 예:
  - `OrderRepository.findByIdAndDeletedAtIsNull(...)`
  - `OrderRepository.findAllByDeletedAtIsNull(...)`

## 9. 언제 fetch join을 쓰는가

- 특정 JPQL 안에서 조인 모양을 명시적으로 통제하고 싶을 때
- repository 메서드 수준에서 “이 쿼리는 연관을 반드시 같이 읽는다”를 분명히 드러내고 싶을 때
- 예:
  - `OrderRepository.findDetailByIdUsingFetchJoin(...)`

주의:

- collection fetch join은 row가 중복될 수 있어 `distinct`를 함께 고려해야 합니다.
- collection fetch join과 pagination을 섞으면 SQL/메모리 측면에서 주의가 필요합니다.

## 10. 언제 `EntityGraph`와 fetch join을 비교해보는가

- 둘 다 연관 로딩을 해결하지만, 선언 위치와 재사용성이 다를 때
- 팀에 “왜 여기서는 EntityGraph를 쓰고, 왜 저기서는 JPQL fetch join을 썼는지” 설명해야 할 때
- 현재 샘플의 기준:
  - `EntityGraph`
    - 메서드 이름 중심 repository API를 유지하고 싶을 때
    - 파생 쿼리나 기본 조회 모양을 최대한 유지할 때
  - fetch join
    - JPQL을 직접 쓰면서 조인 전략을 함께 명시하고 싶을 때
    - 상세 조회 전용 쿼리라는 점을 더 강하게 드러내고 싶을 때

## 11. 언제 파생 쿼리 메서드를 쓰는가

- 조건이 비교적 단순하고 메서드 이름만으로 의도가 충분히 드러날 때
- nested property 조회가 명확할 때
- 예:
  - `findAllByBuyerUsernameAndDeletedAtIsNull(...)`
  - `findAllByDeletedAtIsNullAndStatus(...)`
  - `findAllByBuyerUsernameAndStatusAndDeletedAtIsNull(...)`
  - `findAllByDeletedAtIsNull(...)`

주의:

- optional filter 조합이 많아지면 메서드 개수가 빠르게 늘어날 수 있습니다.
- 지금 샘플에서는 `status`까지는 파생 쿼리로도 읽기 좋지만, 조합이 더 늘어나면 JPQL 조건식이 더 나을 수 있습니다.

## 12. 언제 JPQL `@Query`를 쓰는가

- 단순 파생 쿼리로 표현하기 애매한 집계나 조회 모양이 필요할 때
- 조인 방식이나 반환 shape를 직접 통제하고 싶을 때
- 예:
  - `OrderRepository.findStatusSummaries()`
  - `OrderRepository.findPageWithoutBuyer(...)`
  - `OrderRepository.findDetailByIdUsingFetchJoin(...)`
  - `OrderRepository.searchByConditions(...)`

## 13. 언제 파생 쿼리와 JPQL 조건식을 둘 다 남겨두는가

- 교육용 샘플에서 같은 기능을 두 방식으로 비교해 보여주고 싶을 때
- 팀이 “이 조건 수라면 파생 쿼리로 충분한지, 이제 JPQL로 넘어가야 하는지” 감을 잡아야 할 때
- 현재 샘플의 기준:
  - 파생 쿼리
    - `buyerUsername`, `status`처럼 조합 수가 아직 작고 의도가 이름으로 드러날 때
  - JPQL 조건식
    - nullable filter를 하나의 메서드로 묶고 싶을 때
    - 조건 조합이 늘어날 가능성을 보여주고 싶을 때

## 14. 언제 projection을 쓰는가

- 엔티티 전체를 읽을 필요 없이 읽기 전용 요약값만 필요할 때
- 집계 결과를 바로 화면/API로 전달하고 싶을 때
- 예:
  - `OrderStatusSummaryProjection`
  - `GET /api/orders/status-summary`
  - `GET /api/orders/status-summary?buyerUsername=alice&status=PAID`

주의:

- projection은 쓰기 모델이 아니라 읽기 모델입니다.
- 엔티티 변경이 필요한 유스케이스에는 projection 대신 엔티티 로딩이 필요합니다.

## 15. 언제 N+1 비교를 문서나 테스트에 남기는가

- 기본 LAZY가 맞는지, 특정 조회만 fetch plan을 바꿔야 하는지 팀에 설명해야 할 때
- 성능 이슈가 아니더라도 “왜 EntityGraph를 썼는지” 근거를 남기고 싶을 때
- 예:
  - `OrderRepositoryTest`
    - `findPageWithoutBuyer(...)`
    - `findAllByDeletedAtIsNull(...)`
    - `findDetailByIdUsingFetchJoin(...)`

## 16. 언제 `@Version` 충돌 테스트를 통합 테스트로 남기는가

- 낙관적 락이 실제로 예외를 던지는지 애플리케이션 컨텍스트 수준에서 확인하고 싶을 때
- 단순 엔티티 필드 선언이 아니라 “오래된 스냅샷 저장 시 실패”라는 실제 동작을 증명하고 싶을 때
- 예:
  - `OrderOptimisticLockingIntegrationTest`

## 17. 언제 result DTO를 따로 두는가

- entity를 API로 직접 노출하고 싶지 않을 때
- presentation response와 application output을 분리하고 싶을 때
- 예:
  - `OrderResult`, `OrderSummaryResult`
  - `OrderStatusSummaryResult`
  - `PostResult`
  - `SignupResult`

## 18. 현재 샘플에서 특히 보면 좋은 JPA 조합

- `Post`
  - soft delete + dirty checking
- `User`
  - unique column + registration policy
- `Order`
  - `ManyToOne`
  - `OneToMany`
  - `Embeddable`
  - `Enum`
  - `@Version`
  - 상태 전이 메서드
  - `EntityGraph`
  - fetch join
  - 파생 쿼리 메서드
  - JPQL `@Query`
  - 파생 쿼리 vs JPQL 조건식 비교
  - interface projection
  - N+1 비교 테스트
  - optimistic locking 통합 테스트
  - repository 테스트로 로딩 검증

## 관련 문서

- [JPA 샘플 개요](./jpa-sample-overview.md)
- [아키텍처 경계 원칙](./architecture-boundaries.md)
