# Payment Feature 구현 Plan (Cross-Sample)

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** [`2026-05-15-payment-feature-design.md`](../specs/2026-05-15-payment-feature-design.md)에 정의된 결제 흐름을 양쪽 샘플(kotlin-jpa, java-mybatis)에 동일한 Facade 구조로 점진적 도입한다.

**Strategy:**
- 레벨별 양쪽 샘플 동시 진행 (Level N kotlin + Level N java 끝낸 후 Level N+1로)
- 각 레벨 안에서는: schema → domain → application → presentation → test → http 순
- 매 레벨 종료 시 양쪽 build + 통합 테스트 통과 확인
- 양쪽 모두 task-unit commit (refactor/feat/test/chore 분리)

**Pre-requisites:**
- kotlin: H2 → MySQL 전환 필요 (Level 0)
- java-mybatis: 이미 MySQL 사용 중

**Tech Stack:**
- Spring Boot 3.5, Spring `@Scheduled`, `ApplicationEventPublisher`
- kotlin: Spring Data JPA, Kotest, MockK
- java-mybatis: MyBatis 3.0.5, JUnit 5, Mockito, Rest Assured, Testcontainers

---

## Level 0: 사전 작업 — kotlin MySQL 전환

### Task 0.1: kotlin을 MySQL로 전환

**Files (kotlin):**
- Modify: `build.gradle.kts` — MySQL driver 의존성 추가
- Modify: `src/main/resources/application.yml` — datasource를 MySQL로
- Modify: `src/main/resources/application-sample.yml` — MySQL 예시
- Create: `src/test/resources/application-test.yml` — H2 유지 또는 Testcontainers
- Modify: `src/main/kotlin/.../JavaSpringbootMybatisSampleApplication`(이름은 kotlin) — `@EnableScheduling` 추가
- Modify: `InitializeDataLoader` — MySQL 호환 시드 (현재도 동작하지만 schema mismatch 검토)

- [ ] **Step 1: MySQL driver 의존성 추가**
- [ ] **Step 2: application.yml MySQL 전환** (`jdbc:mysql://localhost:3306/mydatabase`, user/pass `myuser/mypassword`)
- [ ] **Step 3: 단위 테스트는 H2 유지** (application-test.yml)
- [ ] **Step 4: `@EnableScheduling` 추가** (Level 3+ outbox에서 사용)
- [ ] **Step 5: 통합 테스트는 Testcontainers MySQL 도입 검토** (java-mybatis와 동일 패턴)
- [ ] **Step 6: 전체 빌드/테스트 통과**

> 주의: kotlin의 OrderOptimisticLockingIntegrationTest는 현재 H2 in-memory. MySQL 전환 시 Testcontainers 도입 권장.

---

## Level 1: PaymentGateway

### Task 1.1: PaymentGateway 인터페이스 + Mock (양쪽 동시)

**Files (kotlin):**
- Create: `domain/payment/gateway/PaymentGateway.kt`
- Create: `domain/payment/gateway/ApproveResult.kt`
- Create: `domain/payment/gateway/RefundResult.kt`
- Create: `infrastructure/payment/gateway/MockPaymentGateway.kt`
- Create: `domain/payment/exception/PaymentApprovalFailedException.kt`

**Files (java-mybatis):**
- Create: `domain/payment/gateway/PaymentGateway.java`
- Create: `domain/payment/gateway/ApproveResult.java` (record)
- Create: `domain/payment/gateway/RefundResult.java` (record)
- Create: `infrastructure/payment/gateway/MockPaymentGateway.java`
- Create: `domain/payment/exception/PaymentApprovalFailedException.java`

- [ ] **Step 1: PaymentGateway interface 정의**
  - `approve(amount: BigDecimal, idempotencyKey: String): ApproveResult`
  - `refund(paymentKey: String, amount: BigDecimal): RefundResult`
- [ ] **Step 2: ApproveResult / RefundResult value 정의**
  - `ApproveResult(paymentKey, approvedAt)`
  - `RefundResult(refundedAt)`
- [ ] **Step 3: MockPaymentGateway 구현** (성공: UUID 발급, 현재 시각)
- [ ] **Step 4: PaymentApprovalFailedException 정의** (BusinessException 확장, HTTP 422)

### Task 1.2: OrderUseCase.payOrder가 PG 호출하도록 통합

**Files (kotlin):**
- Modify: `application/order/OrderUseCase.kt` — PaymentGateway 주입, approve 호출 후 markPaid
- Modify: 기존 `payOrder` 테스트 — PaymentGateway mock

**Files (java-mybatis):**
- Modify: `application/order/OrderUseCase.java` — PaymentGateway 주입, approve 호출 후 markPaid
- Modify: 기존 `pay` 테스트 — PaymentGateway mock

- [ ] **Step 1: OrderUseCase 생성자에 PaymentGateway 추가**
- [ ] **Step 2: payOrder에서 amount 계산 (order.totalAmount), PG.approve 호출**
- [ ] **Step 3: 성공 시 Order.markPaid(approvedAt) + paymentKey를 Order에 임시 저장 (Level 2에서 Payment로 분리)**
  - kotlin Order에 `paymentKey: String?` 필드 임시 추가 (Level 2에서 제거)
  - 또는 Level 1에서는 paymentKey를 OrderResult로만 반환하고 영속화 안 함 — 둘 중 후자가 단순. **후자 채택**.
- [ ] **Step 4: 실패 시 PaymentApprovalFailedException + Order 상태 전이 안 함**
- [ ] **Step 5: 단위 테스트** — approve 성공/실패 두 케이스
- [ ] **Step 6: 양쪽 build + test 통과**

### Task 1.3: presentation 변경 없음 (Level 1)
- payOrder 응답에 `paymentKey` 추가 정도. 큰 변경 없음.

### Task 1.4: http 갱신
- `http/order.http`의 pay 요청에 주석 추가 ("Level 1: PG mock으로 승인됨")

### Task 1.5: 통합 테스트
- kotlin: 기존 `OrderUseCaseIntegrationTest`에 pay 케이스 추가 (PG mock 자동 와이어링)
- java-mybatis: `OrderCreateMySqlIntegrationTests`에 pay 추가 또는 별도 `OrderPayMySqlIntegrationTests`

### Task 1.6: Level 1 commit & verify
- [ ] kotlin commit: `feat: introduce PaymentGateway mock and wire approve into payOrder`
- [ ] java-mybatis commit: `feat: introduce PaymentGateway mock and wire approve into pay use case`
- [ ] 양쪽 full build/test 통과

---

## Level 2: Payment + PaymentHistory + Idempotency

### Task 2.1: schema migration
- [ ] kotlin: JPA `ddl-auto=update` 활용 + 별도 SQL 없음. Payment/PaymentHistory entity 추가
- [ ] java-mybatis: `init_data.sql` + `db/mysql/payment-schema.sql` 추가
- [ ] 컬럼: payments(id, order_id, idempotency_key UNIQUE, amount, status, payment_key, approved_at, refunded_at, version, created_at, updated_at)
- [ ] payment_histories(id, payment_id, from_status, to_status, occurred_at, reason)

### Task 2.2: Payment aggregate (양쪽 동시)

**Files (kotlin):**
- Create: `domain/payment/Payment.kt`, `PaymentStatus.kt`, `PaymentHistory.kt`
- Create: `domain/payment/repository/PaymentRepository.kt`, `PaymentHistoryRepository.kt`
- Create: `domain/payment/service/PaymentLookupService.kt`
- Create: `domain/payment/exception/PaymentNotFoundException.kt`, `IdempotencyConflictException.kt`

**Files (java-mybatis):**
- Create: `domain/payment/Payment.java`, `PaymentStatus.java`, `PaymentHistory.java`
- Create: `domain/payment/repository/PaymentRepository.java`, `PaymentHistoryRepository.java`
- Create: `infrastructure/payment/PaymentMapper.java`, `PaymentHistoryMapper.java`
- Create: `src/main/resources/mapper/payment/PaymentMapper.xml`, `PaymentHistoryMapper.xml`
- Create: `domain/payment/service/PaymentLookupService.java`
- Create: 예외 클래스들

- [ ] **Step 1: Payment 도메인 클래스 정의** (status 전이 메서드 포함)
- [ ] **Step 2: PaymentHistory 도메인 클래스 정의**
- [ ] **Step 3: Repository port 정의 (양쪽 동일 시그니처)**
  - `save(Payment)`, `findById`, `findByOrderId`, `findByIdempotencyKey`
  - `saveHistory(PaymentHistory)`
- [ ] **Step 4: java-mybatis Mapper + xml 작성**
- [ ] **Step 5: PaymentLookupService 추가**

### Task 2.3: OrderUseCase.payOrder 갱신 — Payment aggregate 사용

**Idempotency-Key 정책: Required (누락 시 400)** — spec 참조.

**핵심 흐름:**
1. `Idempotency-Key` 헤더 수신 (REQUIRED — 누락/빈 문자열은 controller에서 400으로 차단)
2. `paymentRepository.findByIdempotencyKey(key)` → 존재 검증:
   - 같은 order + 같은 amount → 기존 Payment 그대로 replay (PG 호출 없음, 200 OK)
   - 다른 order 또는 같은 order에서 amount 다름 → `IdempotencyConflictException` (409)
3. 없으면 Payment 생성 (REQUESTED 상태) + save
4. PG.approve 호출
5. 성공 시 Payment.markApproved + history insert + Order.markPaid

- [ ] **Step 1: PayOrderCommand에 idempotencyKey 필드 추가**
- [ ] **Step 2: payOrder 흐름 재작성 (key 검증 → replay or new payment)**
- [ ] **Step 3: replay 로직 검증 (같은 키 재요청 시 PG 호출 안 함, 같은 paymentKey 반환)**
- [ ] **Step 4: 다른 order + 같은 키 → IdempotencyConflictException (409)**
- [ ] **Step 5: 같은 order + 같은 키 + 다른 amount → IdempotencyConflictException (409)**

### Task 2.4: presentation
- [ ] kotlin/java-mybatis: payOrder controller에 `@RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey` 추가
  - 누락 → Spring이 자동으로 `MissingRequestHeaderException` → `GlobalExceptionHandler`에서 `IDEMPOTENCY_KEY_REQUIRED` 400으로 변환
- [ ] 빈 문자열 / 255자 초과 검증 → `IDEMPOTENCY_KEY_INVALID` 400
  - controller 또는 application 진입점에서 단순 길이 체크
- [ ] Response에 `PaymentResponse` 노출 (orderId, paymentKey, status, approvedAt)
- [ ] http 파일에 `Idempotency-Key: {{$uuid}}` 헤더 추가
  - 추가 예시: 같은 키 재요청 (replay 동작 시연), 키 누락 (400 시연)

### Task 2.5: 통합 테스트
- [ ] kotlin: PaymentRepositoryTest (JPA), payOrder 멱등성 통합 테스트
  - 케이스: 신규 결제 / replay / 다른 order conflict / 같은 order 다른 amount conflict / 키 누락 400
- [ ] java-mybatis: PaymentMapperMybatisSliceTest, payOrder 멱등성 통합 테스트 (동일 케이스)

### Task 2.6: Level 2 commit & verify
- [ ] 양쪽 각각 4~5개 task-unit commit 분리
- [ ] full build/test 통과

---

## Level 3: Transactional Outbox

### Task 3.1: schema
- [ ] kotlin: OutboxEvent entity (`@Entity`)
- [ ] java-mybatis: `db/mysql/outbox-schema.sql`
- [ ] 컬럼: id, aggregate_type, aggregate_id, event_type, payload (JSON/TEXT), status, retry_count, next_attempt_at, published_at, created_at

### Task 3.2: 도메인 이벤트 정의
- [ ] kotlin: `domain/order/event/OrderPaidEvent.kt` (data class)
- [ ] java-mybatis: `domain/order/event/OrderPaidEvent.java` (record)
- [ ] 필드: orderId, paymentKey, amount, paidAt, eventId

### Task 3.3: OutboxEvent aggregate + repository
- [ ] OutboxEvent 도메인 클래스 (status 전이 메서드 포함)
- [ ] `OutboxEventRepository.findPendingForUpdate(limit)` — `SELECT ... FOR UPDATE SKIP LOCKED LIMIT :limit`
- [ ] kotlin: `@Query(nativeQuery = true)`
- [ ] java-mybatis: `<select>` 안에 raw SQL

### Task 3.4: payOrder에서 outbox insert
- [ ] OrderPaidEvent payload를 Jackson으로 직렬화하여 OutboxEvent로 저장 (같은 트랜잭션)
- [ ] Payment.markApproved + Order.markPaid + OutboxEvent.save → atomicity 보장

### Task 3.5: OutboxPublisher
- [ ] `@Component @EnableScheduling`
- [ ] `@Scheduled(fixedDelay = 1000)` poll
- [ ] 트랜잭션 안에서: findPendingForUpdate(10) → 각각 ApplicationEventPublisher.publishEvent → status=PUBLISHED + published_at 기록
- [ ] 실패 시 status=PENDING 유지 + retry_count++ + next_attempt_at = now + exp_backoff
- [ ] max retry (예: 5회) 초과 시 status=FAILED

### Task 3.6: 통합 테스트
- [ ] payOrder → OutboxEvent 1개 생성 확인
- [ ] OutboxPublisher 1회 실행 후 status=PUBLISHED 확인
- [ ] OutboxPublisher 실패 시뮬레이션 → retry_count 증가 확인 (간단한 케이스만)

### Task 3.7: Level 3 commit & verify

---

## Level 4: Domain Event Consumer

### Task 4.1: FakeSmsSender
- [ ] kotlin/java-mybatis: `infrastructure/notification/FakeSmsSender.kt` / `.java` (logger.info)
- [ ] interface `SmsSender` + `FakeSmsSender` 구현

### Task 4.2: ProcessedEvent
- [ ] schema: processed_events(event_id, consumer_name) — composite PK
- [ ] 도메인 클래스 + repository

### Task 4.3: OrderPaidEventListener
- [ ] `@Component`
- [ ] `@EventListener` 또는 `@TransactionalEventListener(phase = AFTER_COMMIT)`
- [ ] 메서드 시작 시 ProcessedEvent insert 시도 — UNIQUE 충돌 시 skip
- [ ] SmsSender.send 호출

### Task 4.4: 중복 발행 시뮬레이션 테스트
- [ ] 동일 이벤트 2회 publish → SmsSender 1회 호출 확인

### Task 4.5: Level 4 commit & verify

---

## Level 5: Compensation (saga)

### Task 5.1: schema
- [ ] compensation_tasks(id, task_type, payload JSON, status, retry_count, next_attempt_at, created_at)
- [ ] Payment.status에 `REFUND_FAILED` 추가

### Task 5.2: payOrder 보상 트랜잭션
- [ ] try/catch 구조:
  - PG.approve 성공 → Payment.markApproved + ... 시도
  - DB 실패 catch → 별도 트랜잭션(REQUIRES_NEW)으로 PG.refund 호출
  - refund 성공 → Payment.markRefunded + history
  - refund 실패 → CompensationTask insert (task_type=PG_REFUND)

### Task 5.3: CompensationRetryWorker
- [ ] `@Scheduled` 1초 polling
- [ ] PENDING task SELECT FOR UPDATE SKIP LOCKED
- [ ] task_type에 따라 처리 (PG_REFUND → PG.refund 호출)
- [ ] 실패 시 retry_count++ + next_attempt_at = exp_backoff
- [ ] max retry 초과 시 status=FAILED

### Task 5.4: 통합 테스트
- [ ] PG approve 성공 → DB save 강제 실패 → refund 호출 확인
- [ ] refund 실패 → CompensationTask 생성 확인
- [ ] CompensationRetryWorker 실행 후 task status=SUCCESS 확인

### Task 5.5: Level 5 commit & verify

---

## 작업 단위 요약 (commit 단위)

각 레벨당 양쪽 샘플에서 다음과 같이 task-unit commit:

1. `chore: <level> schema`
2. `feat: <level> <aggregate> domain`
3. `feat: <level> <aggregate> repository/mapper`
4. `feat: <level> use case wiring`
5. `feat: <level> presentation/http`
6. `test: <level> unit + integration`

예상 commit 수:
- Level 0: kotlin 2개 (MySQL 전환), java-mybatis 0개
- Level 1: 양쪽 각 3~4개
- Level 2: 양쪽 각 5~6개 (가장 큼)
- Level 3: 양쪽 각 4~5개
- Level 4: 양쪽 각 3~4개
- Level 5: 양쪽 각 4~5개

**총 약 50~60개 commit 예상.**

---

## 진행 checklist (high-level)

- [ ] Level 0: kotlin MySQL 전환
- [ ] Level 1: PaymentGateway (양쪽)
- [ ] Level 2: Payment + Idempotency (양쪽)
- [ ] Level 3: Transactional Outbox (양쪽)
- [ ] Level 4: Domain Event Consumer (양쪽)
- [ ] Level 5: Compensation (양쪽)

---

## 리스크 & 완화

| 리스크 | 완화 |
|---|---|
| Level 0에서 기존 kotlin H2 통합 테스트 다수 깨질 가능성 | Testcontainers MySQL 도입 + 점진적 마이그레이션. 단위 테스트는 H2 유지 |
| Level 3 outbox 동시성 lock 문제 | `SELECT ... FOR UPDATE SKIP LOCKED` 명시. 작은 batch (10건) 사용 |
| Level 5 보상 로직 복잡도 | 학습용: 단일 시나리오만 구현 (PG refund). 운영 시나리오의 폭넓은 fallback은 생략 |
| 양쪽 동시 진행 시 한쪽에만 변경 누락 | 매 task 종료 시 양쪽 모두 build/test 통과 확인 |
| 50+ commit 누적 | 매 레벨 종료 시 push 권장 |

---

## 후속 학습 과제 (out of scope)

- Kafka로 in-process event를 실제 메시지 브로커 분리
- compensation worker를 별도 서비스 분리 → 진짜 saga orchestrator
- Stripe/Toss/Iamport 실제 PG 연동
- 결제 금액 검증 (쿠폰, 포인트, VAT)
- partial refund, multi-currency
