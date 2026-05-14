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
- 시나리오: PG.approve 성공 → DB save 실패 → 자동 PG.refund 호출
  - Spring TransactionSynchronization의 `afterCompletion(STATUS_ROLLED_BACK)` 활용
  - 또는 명시적 try/catch + 보상 호출
- `compensation_tasks` 테이블
  - 컬럼: id, task_type (PG_REFUND/ORDER_CANCEL_FAIL_RECORD), payload (JSON), status (PENDING/SUCCESS/FAILED), retry_count, next_attempt_at, created_at
- 환불 호출도 실패하면 `compensation_tasks`에 task 저장 → 별도 retry worker(`@Scheduled`)가 재시도
- Payment 상태에 `REFUND_FAILED` 추가 (운영자 개입 대상 표시)

**왜 이 범위인가**
- 분산 시스템 일관성 문제의 현실적 해법
- "결제 성공했는데 우리 DB는 모르는" 운영 사고의 해소 패턴

**핵심 결정**
- 보상 트리거: `try/catch + compensation 등록` 방식 (가장 명시적)
- 최대 retry: 3회, 지수 백오프
- 학습용 단순화: retry worker 1초 폴링, DLQ 분리는 생략 (compensation_tasks의 status=FAILED가 DLQ 역할)

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

## 도메인 모델 확장

### Payment (Level 2~)
```
Payment
├── id, orderId, idempotencyKey, amount
├── status: REQUESTED → APPROVED → REFUNDED
├── paymentKey (PG 발급)
├── approvedAt, refundedAt
└── version, createdAt, updatedAt
```

### PaymentHistory (Level 2~)
```
PaymentHistory
├── id, paymentId
├── fromStatus, toStatus, occurredAt, reason
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
├── retryCount, nextAttemptAt, createdAt
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

## 보안 고려사항

- PG mock이라 실제 카드 정보는 다루지 않음
- 멱등성 키는 클라이언트가 신뢰할 수 있는 sender여야 함 (auth 기반 user별 unique 검증은 학습 범위 외)

## 운영 고려사항

- `outbox_events`, `payment_histories`, `compensation_tasks`, `processed_events` 모두 성장 가능 → 실제 운영 시 partition/archival 정책 필요. 학습 샘플은 무한 성장 허용.
- `OutboxPublisher`와 `CompensationRetryWorker`는 각각 `@Scheduled` 사용 → `@EnableScheduling` 필요
- 동시 publisher 인스턴스 가정: `FOR UPDATE SKIP LOCKED`로 안전. 학습 샘플은 단일 인스턴스 가정도 OK.

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
