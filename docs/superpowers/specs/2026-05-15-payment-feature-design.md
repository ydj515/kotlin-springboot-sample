# Payment Feature 설계 (Cross-Sample)

## 개요

주문(Order)에 실제 결제 흐름을 붙여, **UseCase가 다층 서브시스템을 조율하는 Facade 패턴**을 양 샘플(kotlin-jpa, java-mybatis)에서 동일하게 보여준다. 결제는 단순 status 전이가 아닌, PG 호출 / 결제 이력 / 도메인 이벤트 발행 / 보상 트랜잭션까지 production-grade 패턴을 점진적으로 도입한다.

## 배경

현재 `OrderUseCase.payOrder`는 `OrderLookupService` + `OrderStatusTransitionPolicy` 두 개만 조율하므로 Facade 색채가 약하다. 실제 결제 흐름을 도입하면:

- 외부 PG 호출 (network I/O, 실패 가능)
- 결제 이력 audit (멱등성 / 추적성)
- 다른 bounded context에 알림 (도메인 이벤트)
- 분산 트랜잭션 보상 (saga)

이 모든 것을 한 UseCase 메서드 뒤에 숨기는 게 Facade의 본질이다.

또한 cross-sample 학습 가치 극대화:
- 같은 Facade가 JPA vs MyBatis 영속화 차이를 어떻게 흡수하는지
- 같은 도메인 이벤트가 두 ORM에서 어떻게 발행/소비되는지
- 같은 outbox 패턴이 두 영속성 기술에서 어떻게 구현되는지

## 목표

### 1. 기능 목표
- 주문 결제 승인(`approve`), 환불(`refund`) 흐름을 PG mock으로 구현한다.
- 결제 이력을 audit 가능한 형태로 영속화한다.
- 멱등성을 통해 중복 결제 요청을 안전하게 처리한다.
- 결제 성공 시 도메인 이벤트를 안전하게 발행한다(transactional outbox).
- 발행된 이벤트를 다른 컴포넌트가 멱등하게 소비하는 패턴을 보여준다.
- PG 승인 후 DB 실패 시 자동 환불(compensation)을 시도한다.

### 2. 학습 목표 (cross-sample)
- UseCase = Facade임을 두 ORM에서 동일하게 드러낸다.
- production에서 마주치는 결제 시스템 패턴(outbox, idempotent consumer, saga)을 익힌다.
- JPA aggregate 흐름과 MyBatis explicit mapping의 차이를 다층 시나리오에서 관찰한다.

## 비목표
- 실제 PG 연동(Toss/Iamport 등) — mock으로 충분
- 멀티 통화 / 부분 환불 / 부분 결제
- 정밀한 결제 금액 검증 룰(쿠폰, 포인트, 할인)
- 실제 SMS/이메일 발송 — `FakeSmsSender`로 로깅만
- 외부 메시지 브로커(Kafka/RabbitMQ) — in-process로 시뮬레이션

## 5단계 점진적 도입 설계

### Level 1: PaymentGateway (foundation)

**범위**
- `PaymentGateway` 인터페이스 + `MockPaymentGateway` 구현
  - `approve(amount, idempotencyKey) -> ApproveResult(paymentKey, approvedAt)`
  - `refund(paymentKey, amount) -> RefundResult(refundedAt)`
- `payOrder` UseCase가 PG.approve 호출하여 결제 승인 결과를 받아 Order 상태 전이
- PG 실패 시 상태 전이 차단 및 적절한 예외 변환

**왜 이 범위인가**
- Facade의 가장 작은 형태: lookup + validate + **외부 호출** + state mutate
- 외부 의존성을 명시적으로 mock interface 뒤에 두는 패턴 학습

**핵심 결정**
- MockPaymentGateway는 항상 성공 (실패 시뮬레이션은 별도 `FailingMockPaymentGateway` 테스트용 빈)
- `paymentKey`는 UUID-based string

### Level 2: Payment + PaymentHistory (audit + idempotency)

**범위**
- `payments` 테이블: 결제의 현재 상태 1행
  - 컬럼: id, order_id, idempotency_key (UNIQUE), amount, status (REQUESTED/APPROVED/REFUNDED/FAILED), payment_key, approved_at, refunded_at, version, created_at, updated_at
- `payment_histories` 테이블: 상태 전이 audit 로그 N행
  - 컬럼: id, payment_id, from_status, to_status, occurred_at, reason
- 클라이언트 `Idempotency-Key` HTTP 헤더 → 멱등성 보장
- Payment를 **별도 aggregate**로 모델링 (Order는 paymentKey 참조만 보유, 직접 join 없음)

**왜 이 범위인가**
- audit/감사: 금융 도메인 필수 패턴
- 멱등성: at-least-once delivery(클라이언트 재시도, 네트워크 timeout)에서 결제 중복 방지
- aggregate 분리: DDD에서 transaction boundary와 일치하는 단위

**Idempotency-Key 적용 범위**

- **적용**: `POST /api/orders/{id}/pay` (Level 2), `POST /api/orders/{id}/cancel` (Level 5에서 refund compensation trigger)
- **미적용**: `POST /api/orders/{id}/ship` (운영 admin 수동 작업이라 보통 idempotency-key 안 받음. Order.version optimistic lock으로 이중 처리 차단)
- **미적용**: `POST /api/orders` (create), GET 모든 경로 (GET은 정의상 idempotent)
- **이유**: "돈 움직임 + 외부 trigger(환불/알림) 가능 endpoint"만 적용. 실무 다수 패턴 따름. ship은 optimistic lock으로 충분.

**Idempotency-Key 저장 전략 — endpoint별 분리 테이블**

- pay: `payments.idempotency_key UNIQUE`
- cancel: `cancellations.idempotency_key UNIQUE` (Level 5에서 도입; Level 2 시점에는 payments에만)
- 통합 `idempotency_keys` 테이블 사용 안 함 — 도메인 의미가 분명히 드러나는 분리 방식 채택
- 트레이드오프: endpoint 추가 시마다 컬럼 추가 필요. Stripe-style 통합 테이블이 확장성은 더 좋지만 학습용으로는 분리가 명확

**Idempotency-Key 정책 (Required, Stripe 변형)**

본 샘플은 학습/테스트 명확성을 위해 **header required** 정책 채택. 한국 PG 다수 패턴과도 일치.

| 케이스 | 응답 | 비고 |
|---|---|---|
| 헤더 누락 | 400 Bad Request, code=`IDEMPOTENCY_KEY_REQUIRED` | 명확한 학습 신호 |
| 빈 문자열 | 400 동일 | |
| 형식 위반 (255자 초과) | 400, code=`IDEMPOTENCY_KEY_INVALID` | |
| 같은 키 + 같은 order + 같은 amount | 200 OK + 기존 Payment replay (새 PG 호출 없음) | 정상 멱등 동작 |
| 같은 키 + 다른 order | 409 Conflict, code=`IDEMPOTENCY_KEY_CONFLICT` | 키 재사용 방지 |
| 같은 키 + 같은 order + 다른 amount | 409 Conflict, code=`IDEMPOTENCY_KEY_CONFLICT` | 위 변형 (body 변조 차단) |
| 키 TTL | 학습용은 영구 보관 | 실제는 24h 권장 (Stripe 기준) |
| 동시 같은 키 2건 (in-flight) | 두 번째 요청은 DB unique 제약으로 IdempotencyConflictException → 클라이언트 retry 유도 | (학습용 간단 처리, 정교한 lock 도입은 후속) |

**핵심 결정**
- `Payment.status` 전이 시 `payment_histories` 자동 insert (도메인 메서드 안에서)
- 멱등성 키는 **클라이언트 책임** — 서버는 받기만 하고 검증/저장. 형식은 UUID v4 권장 (강제는 아님)
- 키 비교: order_id + idempotency_key 조합 unique. amount 변조 검증 추가

### Level 3: Transactional Outbox

**범위**
- `outbox_events` 테이블
  - 컬럼: id, aggregate_type, aggregate_id, event_type, payload (JSON), status (PENDING/PUBLISHED/FAILED), retry_count, next_attempt_at, published_at, created_at
- `payOrder` 트랜잭션 안에서 `OrderPaidEvent` payload를 outbox에 insert (같은 DB 트랜잭션)
- `OutboxPublisher`: `@Scheduled` polling으로 PENDING 이벤트 읽어 발행
  - 동시 publisher 충돌 방지: `SELECT ... FOR UPDATE SKIP LOCKED` (MySQL 8+) 또는 status를 IN_PROGRESS로 marking
  - 발행 성공 시 status=PUBLISHED + published_at 기록
  - 실패 시 retry_count++ + next_attempt_at 지수 백오프
- max retry 도달 시 status=FAILED (DLQ 역할)

**왜 이 범위인가**
- dual-write 문제 (DB save + 메시지 발행) 해결의 산업 표준
- "at-least-once 발행"의 정확한 구현 예시
- 실제 마이크로서비스 전환 시 그대로 쓰일 패턴

**핵심 결정**
- 발행 = in-process `ApplicationEventPublisher.publishEvent` (Kafka mock 대신)
- 폴링 간격: 1초
- 동시성: MySQL `FOR UPDATE SKIP LOCKED` 사용 (Level 4와 분리 학습 위해 SKIP LOCKED 유지)
- payload: JSON 직렬화 (Jackson)

### Level 4: Domain Event Consumer

**범위**
- `FakeSmsSender` 인터페이스 + 구현 (실제로는 logger.info)
- `OrderPaidEventListener`: outbox publisher가 발행한 이벤트를 받아 SMS 발송 호출
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` 또는 일반 `@EventListener`
- 멱등 소비: `processed_events` 테이블
  - 컬럼: event_id (PK), consumer_name (PK), processed_at
  - 같은 event_id + consumer_name 조합으로 dedupe
- 중복 발행 시뮬레이션: 의도적으로 outbox 발행 2번 → consumer가 1번만 처리해야 함을 확인

**왜 이 범위인가**
- 메시지 발행은 at-least-once → 소비자는 멱등이어야 한다는 원칙 학습
- 실제 Kafka/RabbitMQ 환경 전환 시 그대로 적용

**핵심 결정**
- in-process Spring event로 발행 (Level 3에서 결정)
- consumer는 메서드 시작 시 `processed_events`에 insert 시도, UNIQUE 충돌 시 skip
- 의도적 Level 3과 분리 유지 → outbox = 발행 책임, consumer = 소비 책임 명확히 구분

### Level 5: Compensation (saga)

**범위**
- 시나리오 A (PG approve 실패): `Payment.markFailed` → 보상 불필요
- 시나리오 B (PG approve 성공 → DB 실패): 자동 PG.refund 시도 → 성공 시 `Payment.REFUNDED` / 실패 시 `CompensationTask` 등록 + `Payment.REFUND_FAILED`
- 시나리오 C (cancel on PAID order): PG.refund 호출 → 성공 시 `Payment.REFUNDED` + `Order.CANCELLED` / 실패 시 동일 보상 패턴
- 시나리오 D (cancel on CREATED, 미결제): refund 호출 없음 → `Order.CANCELLED`만
- 신규 테이블: `compensation_tasks`, `cancellations`
- 신규 클래스: `CompensationService` (보상 트랜잭션 전담), `CompensationRetryWorker` (`@Scheduled`)
- `Payment.REFUND_FAILED` 상태 추가

**왜 이 범위인가**
- 분산 시스템 일관성 문제의 현실적 해법
- "결제 성공했는데 우리 DB는 모르는" 운영 사고의 해소 패턴
- cancel = 단순 상태 전이가 아니라 환불(=PG 외부 호출)을 동반 → Level 5에서 idempotency까지 함께 도입이 자연스러움

---

#### 5.1 보상 트리거 메커니즘 — 명시적 try/catch 채택

| 옵션 | 채택? | 이유 |
|---|---|---|
| try/catch + 명시적 `CompensationService` 호출 | **채택** | 흐름 명시적/테스트 용이/디버깅 명확 |
| `TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)` | 거부 | 트랜잭션 시스템 hook은 마법 같음. 학습용으로는 흐름이 숨겨져 부적합 |
| `ApplicationEventPublisher.publishEvent` + listener에서 보상 | 거부 | 트랜잭션 롤백 시 정상 발행되지 않거나 `AFTER_ROLLBACK` phase로만 가능 → 흐름 추적 어려움 |

**구현 패턴** (`OrderUseCase.payOrder` 내부):

```kotlin
val payment = paymentRepository.save(Payment.newRequested(orderId, key, amount))
val approveResult = try {
    paymentGateway.approve(payment.amount, key)
} catch (e: PaymentApprovalFailedException) {
    payment.markFailed(reason = e.message)
    paymentRepository.save(payment)
    throw e  // 시나리오 A — 보상 불필요
}

// 시나리오 B 진입: 여기 이후 실패는 모두 보상 대상
try {
    payment.markApproved(approveResult.paymentKey, approveResult.approvedAt)
    paymentRepository.save(payment)
    order.markPaid(approveResult.approvedAt)
    orderRepository.save(order)
    publishOrderPaidEventToOutbox(order, payment)
    return OrderResult.from(order, payment)
} catch (e: Exception) {
    // 메인 트랜잭션은 롤백 예정. 보상은 별도 트랜잭션으로.
    compensationService.compensateApprovedPayment(
        paymentId = payment.id!!,
        paymentKey = approveResult.paymentKey,
        amount = payment.amount,
        reason = "payOrder downstream failure: ${e.message}"
    )
    throw e
}
```

#### 5.2 CompensationService 별도 클래스 분리

`OrderUseCase`는 Facade 역할 유지. 보상 트랜잭션 자체는 별도 `@Service CompensationService`에 위임.

| 메서드 | propagation | 책임 |
|---|---|---|
| `compensateApprovedPayment(paymentId, paymentKey, amount, reason)` | `REQUIRES_NEW` | PG.refund 호출 → 성공 시 `Payment.markRefunded` / 실패 시 `Payment.markRefundFailed` + `CompensationTask` insert |
| `processCompensationTask(task)` | `REQUIRES_NEW` | `CompensationRetryWorker`가 호출. `PG_REFUND` task 재시도. 성공 시 task SUCCESS + Payment.REFUNDED / 실패 시 retry_count++ |

**근거**:
- Spring `@Transactional(REQUIRES_NEW)`은 self-invocation으로 작동하지 않음 → 별도 빈 분리 필수
- 책임 분리: `OrderUseCase`는 정상 흐름 + 보상 trigger, `CompensationService`는 보상 자체
- 테스트: PG mock + spy/mocked repository로 시나리오 B 단위 검증 가능

#### 5.3 CompensationRetryWorker — SKIP LOCKED 채택

`OutboxPublisher`와 동일 패턴 (`SELECT ... FOR UPDATE SKIP LOCKED`):

- 동시 worker 인스턴스 가정 (학습 일관성). 단일 인스턴스 운영 시도 같은 코드로 안전
- H2 단위 테스트: syntax만 통과(MODE=MySQL 의존). 실제 동시성 검증은 MySQL 통합 테스트에서만
- 폴링 간격: `app.compensation.worker.fixed-delay-ms` (기본 1000ms)
- BATCH_SIZE = 10
- MAX_RETRY = 3, exp backoff `2^n` 초 (최대 60초)

#### 5.4 Max retry 초과 처리 — status=FAILED만 (학습 범위)

`compensation_tasks.status = FAILED` 전이 + `log.error` 출력으로 종결.

- DLQ 분리 테이블: 만들지 않음 — `status=FAILED`가 DLQ 역할
- 운영자 개입 시: 수동으로 `status=PENDING + retry_count=0`으로 갱신 → worker가 재처리
- 운영 알림: `OncallAlerter` 같은 인터페이스 + Slack/Email integration은 **본 샘플 범위 외**. 후속 학습 과제 (운영 고려사항 섹션 참조)

#### 5.5 시나리오별 Payment / Order / CompensationTask 상태 매트릭스

| 시나리오 | Payment.status 종결 | Order.status 종결 | CompensationTask | 후속 |
|---|---|---|---|---|
| A. PG approve 실패 | FAILED | (변경 없음) | 없음 | 클라이언트 재요청은 **새 Idempotency-Key + 새 Payment** |
| B-1. approve 성공, DB 실패, refund 성공 | REFUNDED | (변경 없음, 메인 롤백) | 없음 | 클라이언트는 5xx 받음. payOrder 재요청은 새 키로 |
| B-2. approve 성공, DB 실패, refund 실패 | REFUND_FAILED | (변경 없음) | PG_REFUND PENDING | Worker 재시도. 성공 시 Payment.REFUNDED + task SUCCESS, max retry 초과 시 task FAILED |
| C-1. cancel(PAID), refund 성공 | REFUNDED | CANCELLED | 없음 | cancellation.status=SUCCEEDED |
| C-2. cancel(PAID), refund 실패 | REFUND_FAILED | CANCELLED | PG_REFUND PENDING | cancellation.status=REFUND_FAILED. Worker 재시도 |
| D. cancel(CREATED, 미결제) | (해당 Payment 없음) | CANCELLED | 없음 | refund 호출 안 함 |

> **짧은 DB 트랜잭션 + 외부 PG 호출 분리 근거 (Stripe PaymentIntent 패턴)**:
> `OrderUseCase.payOrder`는 트랜잭션을 열지 않는 오케스트레이션 계층으로 두고, `OrderPaymentTransactionService`가 `Payment.REQUESTED` 저장, `Payment.APPROVED`/`FAILED` 기록, `Order.markPaid + OutboxEvent.save`를 각각 짧은 트랜잭션으로 처리한다. PG approve/refund는 어떤 DB 트랜잭션 안에서도 호출하지 않는다. 이렇게 해야 외부 호출 지연으로 DB 커넥션/락을 오래 점유하지 않으면서도 Payment audit을 보존할 수 있다.
>
> 참고: `compensateApprovedPayment`도 PG.refund를 먼저 트랜잭션 밖에서 호출하고, 환불 성공/실패 기록과 CompensationTask 등록만 짧은 트랜잭션으로 수행한다.

#### 5.6 cancellations 테이블 schema

```
cancellations
├── id (PK, AUTO_INCREMENT)
├── order_id BIGINT NOT NULL FK→purchase_orders(id)
├── idempotency_key VARCHAR(255) UNIQUE
├── reason VARCHAR(500) nullable
├── status VARCHAR(30) NOT NULL — REQUESTED / SUCCEEDED / REFUND_FAILED
├── refunded_at DATETIME nullable — 환불 성공 시점 (C-1 / C-2 retry 성공)
├── version BIGINT NOT NULL DEFAULT 0
├── created_at, updated_at
```

- replay: 같은 key + 같은 order → 기존 cancellation 결과 그대로 반환
- conflict: 같은 key + 다른 order → `IdempotencyConflictException` (409)
- conflict: 같은 key + 같은 order + 다른 reason → `IdempotencyConflictException` (Stripe 변형. 본질 필드 변조 차단)

#### 5.7 compensation_tasks 테이블 schema

```
compensation_tasks
├── id (PK, AUTO_INCREMENT)
├── task_type VARCHAR(50) NOT NULL — PG_REFUND (현재 단일, 확장 가능)
├── payload TEXT/CLOB NOT NULL — JSON: {"paymentId":..,"paymentKey":..,"amount":..,"reason":..}
├── status VARCHAR(20) NOT NULL — PENDING / SUCCESS / FAILED
├── retry_count INT NOT NULL DEFAULT 0
├── next_attempt_at DATETIME NOT NULL
├── last_error VARCHAR(1000) nullable
├── created_at, updated_at
└── INDEX (status, next_attempt_at) — worker 조회 최적화
```

- `task_type` enum 확장 여지: `SMS_RETRY`, `ORDER_CLEANUP` 등. 현 학습 범위는 `PG_REFUND`만

#### 5.8 트랜잭션 경계 (Level 5 추가분 포함 정리)

| 호출 | propagation | 비고 |
|---|---|---|
| `OrderUseCase.payOrder` | 없음 | 결제 오케스트레이션. PG.approve는 트랜잭션 밖에서 호출 |
| `OrderPaymentTransactionService.preparePayOrder` | REQUIRED | 주문 row를 `PESSIMISTIC_WRITE`로 짧게 잠그고 Payment.REQUESTED 저장. 동일 주문의 REQUESTED/APPROVED payment가 있으면 차단 |
| `OrderPaymentTransactionService.markPaymentFailed` | REQUIRED | PG approve 명시 실패 시 Payment.FAILED 기록 |
| `OrderPaymentTransactionService.markPaymentApproved` | REQUIRED | PG approve 성공 후 Payment.APPROVED audit 기록 |
| `OrderPaymentTransactionService.completePayOrder` | REQUIRED | Order.markPaid + OutboxEvent.save 원자 처리. 실패 시 호출자는 refund 보상 |
| `OrderUseCase.cancelOrder` | 없음 | 취소 오케스트레이션. PG.refund는 트랜잭션 밖에서 호출 |
| `OrderPaymentTransactionService.prepareCancelOrder` | REQUIRED | cancellation insert + Order CANCELLED 전이를 짧은 트랜잭션으로 처리 |
| `CompensationService.compensateApprovedPayment` | 없음 | PG.refund를 트랜잭션 밖에서 호출 |
| `CompensationTransactionService.*` | REQUIRES_NEW/readonly | 환불 결과 기록, CompensationTask claim/update만 짧은 트랜잭션으로 처리 |
| `CompensationRetryWorker.runBatch` | 없음 | task claim 후 PG.refund 재시도는 트랜잭션 밖에서 수행 |
| `OutboxPublisher.publish` (배치 단위) | REQUIRES_NEW | (Level 3 기존) |
| `ProcessedEventService.tryMarkProcessed` | REQUIRES_NEW | 이벤트 dedup 기록만 짧은 트랜잭션으로 처리. SMS 전송은 트랜잭션 밖에서 수행 |

#### 5.9 테스트 전략 — DB 실패 시뮬레이션

**kotlin (Kotest + MockK)**:
```kotlin
@MockkBean lateinit var orderRepository: OrderRepository
every { orderRepository.save(any()) } throws DataAccessException("simulated")

shouldThrow<DataAccessException> { orderUseCase.payOrder(command) }

// 검증
verify { paymentGateway.refund(approveResult.paymentKey, amount) }
val payment = paymentRepository.findByIdempotencyKey(key)!!
payment.status shouldBe PaymentStatus.REFUNDED
```

**java-mybatis (JUnit 5 + Mockito)**:
```java
@MockBean OrderMapper orderMapper;
when(orderMapper.updateStatusToPaid(...)).thenThrow(new DataAccessException("simulated") {});

assertThatThrownBy(() -> orderUseCase.pay(orderId, key))
    .isInstanceOf(DataAccessException.class);

verify(paymentGateway).refund(approveResult.paymentKey(), amount);
Payment payment = paymentRepository.findByIdempotencyKey(key).orElseThrow();
assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
```

refund도 mock으로 실패시키면 `compensation_tasks` PENDING 레코드 1건이 별도 트랜잭션으로 저장됐는지 확인 (REQUIRES_NEW가 메인 롤백과 독립 commit하는지 검증).

## 두 샘플의 비교 포인트

| 측면 | kotlin (JPA) | java-mybatis (MyBatis) |
|---|---|---|
| Payment 저장 | `PaymentRepository : JpaRepository<Payment, Long>` + save | `PaymentMapper extends PaymentRepository` + xml insert |
| PaymentHistory 저장 | aggregate 안에서 cascade 또는 별도 save | 명시적 insertHistory 호출 |
| Outbox SELECT FOR UPDATE SKIP LOCKED | `@Query` + nativeQuery | `<select>` + raw SQL |
| Outbox 동시성 격리 수준 | `@Transactional(isolation = ...)` | 동일 |
| ApplicationEventPublisher 사용 | 동일 | 동일 |
| 도메인 이벤트 정의 | Kotlin data class | Java record |
| compensation_tasks 처리 | JpaRepository + custom update | Mapper + update xml |
| cancellation 저장 | JpaRepository | Mapper + xml |
| CompensationService 빈 분리 | 동일 (`@Service`) | 동일 |
| Facade(UseCase) 시그니처 | 동일 | 동일 |
| Transaction propagation | 동일 (`@Transactional(REQUIRES_NEW)` 등) | 동일 |

**학습 포인트의 핵심**:
- application 계층(Facade) 모양은 거의 동일 → 추상화의 힘
- infrastructure 계층(repository/mapper)에서만 차이 → 영속성 기술 차이의 위치 명확화

## 공통 아키텍처 규약 적용

- `Payment`는 별도 aggregate (`domain/payment/`)
- `Payment`/`PaymentHistory`/`OutboxEvent`/`ProcessedEvent`/`CompensationTask` 각각 자체 repository port + adapter
- `PaymentUseCase` 또는 `OrderUseCase.payOrder`에 Facade 책임 통합 — **`OrderUseCase.payOrder`로 유지**가 좋다 (결제는 주문의 흐름)
- 도메인 이벤트: `domain/order/event/OrderPaidEvent` (생성자가 Order이므로 order package)
- outbox/compensation: `infrastructure/outbox/`, `infrastructure/compensation/` (인프라 횡단 관심사)

## 트랜잭션 경계

| Level | 트랜잭션 경계 |
|---|---|
| 1 | `payOrder`: PG 호출 → 상태 전이 (단일 트랜잭션 안에 외부 호출 → 위험. Level 5에서 보완) |
| 2 | `payOrder`: Payment.create → PG 호출 → Payment.markApproved → PaymentHistory insert (단일 트랜잭션) |
| 3 | `payOrder`: 위 + OutboxEvent insert (모두 같은 트랜잭션, atomicity 보장) |
| 3 | `OutboxPublisher.publish`: PENDING SELECT FOR UPDATE SKIP LOCKED → publish → status PUBLISHED 업데이트 (배치 1건 = 1 트랜잭션) |
| 4 | `OrderPaidEventListener.handle`: ProcessedEvent insert (UNIQUE 충돌 시 abort) → SMS 전송 (별도 트랜잭션 - REQUIRES_NEW) |
| 5 | `payOrder` 안에서 PG.approve 후 DB 실패 catch → 별도 트랜잭션으로 PG.refund 호출 → 실패 시 CompensationTask insert |

## Payment aggregate 설계 결정 (Level 2 진입 전 확정)

### 1. OrderId 참조 방식 — ID-only reference + DB FK

**채택**: `payments.order_id BIGINT NOT NULL` + DB FK `→ orders(id)`. 도메인 객체는 `Order` 인스턴스를 들지 않고 **`orderId: Long`만 보유**.

**근거**:
- DDD aggregate 경계 원칙: 다른 aggregate는 ID로만 참조 (Vaughn Vernon)
- JPA `@ManyToOne Order` 매핑 회피 → Payment aggregate가 Order aggregate를 lazy-loading 통해 종속되는 사고 방지
- DB FK는 유지 → 단일 DB 환경에서 데이터 무결성 보장 (microservice 분리 시점에 제거 검토)

**구현**:
- kotlin: `class Payment(val orderId: Long, ...)`, `@Column(name = "order_id") val orderId: Long`. NO `@ManyToOne`.
- java-mybatis: `private Long orderId;` + Mapper에서 SELECT/INSERT 시 단순 컬럼

**비고**:
- Order → Payment 역참조도 동일 원칙 (`Order`는 `paymentIds`나 nothing). 학습용으로는 Order가 Payment를 모르는 게 깔끔.
- Order.status를 PAID로 전이하는 책임은 `OrderUseCase` (Facade)가 담당. Order/Payment 둘 다 변경하는 트랜잭션 안에서.

### 2. Payment.status enum 전이도

**채택 상태**:
- `REQUESTED`: Payment 객체 생성 + DB insert 직후. PG.approve 호출 전/중.
- `APPROVED`: PG.approve 성공 → paymentKey, approvedAt 채워짐.
- `FAILED`: PG.approve 실패. paymentKey null, 재시도하려면 새 Payment.
- `REFUNDED`: PG.refund 성공 (Level 5에서 도입).
- `REFUND_FAILED`: PG.refund 실패 (Level 5에서 도입). compensation_tasks에 등록되어 retry worker가 처리.

**전이 매트릭스**:

| From → To | REQUESTED | APPROVED | FAILED | REFUNDED | REFUND_FAILED |
|---|---|---|---|---|---|
| (new) | ✓ insert | | | | |
| REQUESTED | | ✓ PG.approve 성공 | ✓ PG.approve 실패 | | |
| APPROVED | | | | ✓ PG.refund 성공 (L5) | ✓ PG.refund 실패 (L5) |
| FAILED | (terminal) | | | | |
| REFUNDED | (terminal) | | | | |
| REFUND_FAILED | | | | ✓ retry 성공 (L5) | (재실패 시 자기 자신 유지) |

**금지 전이**: 위에 없는 모든 조합. 도메인 메서드(`markApproved`, `markFailed`, `markRefunded`, `markRefundFailed`)가 호출 시점에 현재 status 검증 후 던짐 → `IllegalPaymentStateTransitionException`.

**구현 메서드 시그니처**:
- `markApproved(paymentKey: String, approvedAt: LocalDateTime)`: REQUESTED → APPROVED
- `markFailed(reason: String, occurredAt: LocalDateTime)`: REQUESTED → FAILED
- `markRefunded(refundedAt: LocalDateTime, reason: String)`: APPROVED 또는 REFUND_FAILED → REFUNDED (Level 5)
- `markRefundFailed(reason: String, occurredAt: LocalDateTime)`: APPROVED → REFUND_FAILED (Level 5)

### 3. PaymentHistory 저장 트리거 위치 — Payment aggregate 안

**채택**: Payment 도메인 메서드(`markApproved`, `markFailed` 등) 내부에서 in-memory `histories: MutableList<PaymentHistory>`에 PaymentHistory 객체를 추가. Repository.save는 Payment 본체와 누적된 histories를 함께 영속화.

**근거**:
- 도메인이 자신의 audit을 책임 — DDD 친화적
- UseCase 코드가 매번 history 호출하는 부담/누락 위험 제거
- transaction 안에서 atomic 보장

**JPA vs MyBatis 차이**:

| 측면 | kotlin (JPA) | java-mybatis (MyBatis) |
|---|---|---|
| in-memory 누적 | `Payment.histories: MutableList<PaymentHistory>` (transient or @OneToMany) | `Payment.histories: List<PaymentHistory>` (transient field, MyBatis는 자동 매핑 안 함) |
| 영속화 | `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)` — Payment.save 시 cascade | `PaymentRepository.save(payment)` 내부 구현에서 paymentMapper.update + 새로 추가된 histories만 paymentHistoryMapper.insertHistory |
| 기존 history vs 신규 | JPA가 알아서 detect (transient = new) | 신규 추가된 항목만 추적하기 위해 Payment에 `newlyAddedHistories` 같은 transient list 별도 관리 |

**도메인 메서드 예시** (kotlin):
```kotlin
fun markApproved(paymentKey: String, approvedAt: LocalDateTime, reason: String = "PG approved") {
    require(status == PaymentStatus.REQUESTED) {
        throw IllegalPaymentStateTransitionException(status, PaymentStatus.APPROVED)
    }
    val fromStatus = status
    this.paymentKey = paymentKey
    this.approvedAt = approvedAt
    this.status = PaymentStatus.APPROVED
    histories.add(PaymentHistory.of(this, fromStatus, status, approvedAt, reason))
}
```

**대안 (Spring ApplicationEvent + listener)**:
- 도메인 이벤트로 발행 후 listener가 history 저장
- 학습용으로는 over-engineering. Level 3~4의 outbox/consumer 패턴과 헷갈리기 쉬움
- → **채택 안 함**

### 4. payments 테이블 UNIQUE 전략

- `idempotency_key VARCHAR(255) UNIQUE` (전역 unique — Stripe 패턴)
- 다른 order에서 같은 키 사용 → DB UNIQUE 위반 → `IdempotencyConflictException` (409)
- 같은 order + 같은 키 → 검색 후 amount 일치 검증 → 일치하면 replay, 다르면 conflict (409)

### 5. 추가 결정

- **amount precision**: `DECIMAL(12, 2)` — orders.total_amount와 일치
- **version**: 모든 mutable 상태에 대해 optimistic lock (`@Version` / mybatis는 update WHERE version=?)
- **idempotency_key 길이**: VARCHAR(255), application에서 1~255자 검증
- **paymentKey 길이**: VARCHAR(100), PG 발급. MockPaymentGateway는 `MOCK-PG-<uuid>` 형태 (총 ~44자)

---

## 도메인 모델 확장

### Payment (Level 2~)
```
Payment
├── id (PK, AUTO_INCREMENT)
├── orderId (FK to orders.id, NOT NULL)
├── idempotencyKey (VARCHAR(255), UNIQUE GLOBALLY)
├── amount (DECIMAL(12,2), NOT NULL)
├── status (VARCHAR(30), NOT NULL) — REQUESTED/APPROVED/FAILED/REFUNDED/REFUND_FAILED
├── paymentKey (VARCHAR(100), nullable) — PG 발급, REQUESTED 단계엔 null
├── approvedAt, refundedAt (DATETIME, nullable)
├── version (BIGINT, optimistic lock)
└── createdAt, updatedAt (BaseEntity 패턴)

(transient/in-memory)
└── histories: List<PaymentHistory> — 도메인 메서드가 누적, repository.save 시 함께 flush
```

### PaymentHistory (Level 2~)
```
PaymentHistory
├── id (PK)
├── paymentId (FK to payments.id, NOT NULL)
├── fromStatus, toStatus (VARCHAR(30))
├── occurredAt (DATETIME, NOT NULL)
└── reason (VARCHAR(255), nullable) — 예: "PG approved", "PG declined: insufficient balance"
```

### OutboxEvent (Level 3~)
```
OutboxEvent
├── id, aggregateType, aggregateId, eventType
├── payload (JSON)
├── status: PENDING → PUBLISHED → FAILED
├── retryCount, nextAttemptAt, publishedAt, createdAt
```

### ProcessedEvent (Level 4~)
```
ProcessedEvent (PK: eventId + consumerName)
├── eventId, consumerName
├── processedAt
```

### CompensationTask (Level 5~)
```
CompensationTask
├── id, taskType, payload (JSON)
├── status: PENDING → SUCCESS → FAILED
├── retryCount, nextAttemptAt, lastError, createdAt, updatedAt
```

### Cancellation (Level 5~)
```
Cancellation
├── id, orderId, idempotencyKey (UNIQUE), reason
├── status: REQUESTED → SUCCEEDED / REFUND_FAILED
├── refundedAt, version, createdAt, updatedAt
```

## 도메인 이벤트

| 이벤트 | 발생 조건 | 소비자 |
|---|---|---|
| `OrderPaidEvent` | Payment.status = APPROVED + Order.status = PAID 전이 시 | `OrderPaidEventListener` → FakeSmsSender |
| `OrderPaymentFailedEvent` | PG 실패 또는 compensation 실패 시 (Level 5) | (학습용) 로그만 |

## API 변경

### POST /api/orders/{id}/pay
**Request**:
- Header: `Idempotency-Key: <client-generated UUID>` (Level 2~, **REQUIRED**)
- Body: (없음) — order id로 amount는 서버 계산

**Response**:
- 200 OK
- Body: PaymentResponse (orderId, paymentKey, status, approvedAt)

**오류 (Level 2~)**:
- 400 `IDEMPOTENCY_KEY_REQUIRED`: 헤더 누락 또는 빈 문자열
- 400 `IDEMPOTENCY_KEY_INVALID`: 형식 위반 (255자 초과 등)
- 409 `IDEMPOTENCY_KEY_CONFLICT`: 멱등성 키가 다른 order에서 이미 사용됨, 또는 같은 order인데 amount가 다름
- 409 `ORDER_ALREADY_PAID` (또는 상태 전이 위반): 이미 결제된 주문
- 422 `PAYMENT_APPROVAL_FAILED`: PG 승인 실패 (Level 1~)

**Replay 동작 (멱등 정상 케이스)**:
- 같은 키 + 같은 order + 같은 amount → 200 OK + 기존 Payment replay (PG 호출 없음)
- 500: PG 호출 실패 + 보상 처리 중 (Level 5)

### POST /api/orders/{id}/cancel (Level 5~)

**Request**:
- Header: `Idempotency-Key: <client-generated UUID>` (**REQUIRED**)
- Body: 선택 — `{"reason": "..."}`

**Response**:
- 200 OK
- Body: OrderResponse (status=CANCELLED, cancelledAt, cancelReason, 이미 결제됐다면 refund 처리 결과 포함)

**오류**:
- 400 `IDEMPOTENCY_KEY_REQUIRED` / `IDEMPOTENCY_KEY_INVALID`: pay와 동일 규칙
- 409 `IDEMPOTENCY_KEY_CONFLICT`: 다른 order에서 이미 사용된 키, 또는 같은 order인데 reason 등 본질 필드가 다름
- 409: 이미 취소된 주문 또는 취소 불가 상태 (SHIPPED 등)
- 500: PG 환불 실패 (Level 5 compensation_tasks로 전환)

**저장**: `cancellations.idempotency_key UNIQUE` 컬럼 (Level 5 도입). Replay 시 기존 cancel 결과 그대로 반환.

### POST /api/orders/{id}/ship — idempotency 미적용

- `Order.version` 기반 optimistic locking으로 이중 처리 차단.
- 운영 admin 수동 작업 컨텍스트라 별도 멱등성 키 받지 않음.
- 알림(SMS)은 Level 4 outbox + idempotent consumer로 보호 (이벤트 레벨 멱등).

## 보안 고려사항

- PG mock이라 실제 카드 정보는 다루지 않음
- 멱등성 키는 클라이언트가 신뢰할 수 있는 sender여야 함 (auth 기반 user별 unique 검증은 학습 범위 외)

## 운영 고려사항

- `outbox_events`, `payment_histories`, `compensation_tasks`, `cancellations`, `processed_events` 모두 성장 가능 → 실제 운영 시 partition/archival 정책 필요. 학습 샘플은 무한 성장 허용.
- `OutboxPublisher`와 `CompensationRetryWorker`는 각각 `@Scheduled` 사용 → `@EnableScheduling` 필요
- 동시 publisher 인스턴스 가정: `FOR UPDATE SKIP LOCKED`로 안전. 학습 샘플은 단일 인스턴스 가정도 OK.
- **OncallAlerter (후속 학습 과제)**: compensation_tasks가 max retry 초과로 `FAILED`로 전이될 때, 실제 운영에서는 oncall 엔지니어에게 알림이 가야 함 (Slack/PagerDuty/Email). 본 샘플은 `log.error`만 출력하며, `OncallAlerter` 같은 domain port + Slack adapter는 도입하지 않음. 도입 시 권장 위치: `CompensationService.processCompensationTask` 안에서 max retry 도달 직후 호출.
- **CompensationTask payload 변조 방지**: 학습 샘플은 JSON 평문 저장. 운영에서는 schema versioning(`payload_version` 컬럼) + 역직렬화 실패 시 task 자체 FAILED 전환 정책 필요.

## 회피해야 할 함정

- **동기 호출 안에 외부 API**: PG 호출이 같은 트랜잭션에 있으면 DB lock을 너무 오래 잡음. Level 5에서 명시적 보상으로 해결.
- **이벤트 발행을 컴퓨터 메모리에만**: in-process `ApplicationEventPublisher`만 쓰면 process crash 시 이벤트 유실. → outbox로 해결 (Level 3).
- **at-most-once 소비 가정**: outbox는 at-least-once 보장 → consumer 멱등성 필수 (Level 4).
- **Payment를 Order의 필드로 통합**: aggregate가 너무 커지고 트랜잭션 경계 모호. 별도 aggregate 유지.

## 두 샘플의 변형 허용

- kotlin: `OrderPaidEvent` = `data class`, MyBatis는 `record`
- kotlin Repository: Spring Data JPA convention
- MyBatis Repository: port + mapper adapter 그대로
- 둘 다 `ApplicationEventPublisher` (Spring 표준)는 동일

## 외부 의존성

- 양쪽: Jackson (이미 있음)
- 양쪽: Spring `@Scheduled` (kotlin은 신규 추가 필요)
- 양쪽: `Idempotency-Key` 헤더 수신은 표준 Spring `@RequestHeader`

## 미해결 / 후속 검토

- 실제 결제 금액 계산 룰 (쿠폰/할인) 도입 여부
- 결제 partial refund 지원 여부
- compensation worker를 별도 프로세스로 분리하는 microservice 변형 — 후속 학습 과제로
- `OrderPaidEvent` 외에 `PaymentApprovedEvent`도 별도 발행할지 — 현재는 결제와 주문 상태가 1:1이라 단일 이벤트로 충분
