package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.compensation.CompensationOutcome
import com.example.kotlinspringbootsample.application.compensation.CompensationService
import com.example.kotlinspringbootsample.application.payment.PaymentLifecycleService
import com.example.kotlinspringbootsample.application.order.command.CancelOrderCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrdersCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrderStatusSummariesCommand
import com.example.kotlinspringbootsample.application.order.command.OrderSearchMode
import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.application.order.command.ShipOrderCommand
import com.example.kotlinspringbootsample.domain.customer.Customer
import com.example.kotlinspringbootsample.domain.customer.service.CustomerLookupService
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.ShippingAddress
import com.example.kotlinspringbootsample.domain.order.exception.InvalidOrderStatusTransitionException
import com.example.kotlinspringbootsample.domain.order.policy.OrderItemPolicy
import com.example.kotlinspringbootsample.domain.order.policy.OrderStatusTransitionPolicy
import com.example.kotlinspringbootsample.domain.order.repository.CancellationRepository
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.order.repository.projection.OrderStatusSummaryProjection
import com.example.kotlinspringbootsample.domain.order.service.OrderLookupService
import com.example.kotlinspringbootsample.domain.outbox.OutboxEvent
import com.example.kotlinspringbootsample.domain.outbox.repository.OutboxEventRepository
import com.example.kotlinspringbootsample.domain.payment.Payment
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.exception.IdempotencyConflictException
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentApprovalFailedException
import com.example.kotlinspringbootsample.domain.payment.gateway.ApproveResult
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

class OrderUseCaseTest : BehaviorSpec({

    val orderRepository = mockk<OrderRepository>()
    val customerLookupService = mockk<CustomerLookupService>()
    val orderLookupService = mockk<OrderLookupService>()
    val paymentGateway = mockk<PaymentGateway>()
    val paymentRepository = mockk<PaymentRepository>()
    val outboxEventRepository = mockk<OutboxEventRepository>()
    val compensationService = mockk<CompensationService>()
    val cancellationRepository = mockk<CancellationRepository>()
    val paymentLifecycleService = mockk<PaymentLifecycleService>()
    val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    val orderUseCase = OrderUseCase(
        orderRepository = orderRepository,
        customerLookupService = customerLookupService,
        orderLookupService = orderLookupService,
        orderItemPolicy = OrderItemPolicy(),
        orderStatusTransitionPolicy = OrderStatusTransitionPolicy(),
        paymentGateway = paymentGateway,
        paymentRepository = paymentRepository,
        outboxEventRepository = outboxEventRepository,
        objectMapper = objectMapper,
        compensationService = compensationService,
        cancellationRepository = cancellationRepository,
        paymentLifecycleService = paymentLifecycleService
    )

    beforeTest {
        clearMocks(
            orderRepository, customerLookupService, orderLookupService,
            paymentGateway, paymentRepository, outboxEventRepository,
            compensationService, cancellationRepository, paymentLifecycleService
        )
    }

    Given("주문 결제 요청이 들어오면") {
        val idempotencyKey = "550e8400-e29b-41d4-a716-446655440000"

        When("idempotencyKey가 신규이고 주문이 CREATED 상태면") {
            Then("Payment.REQUESTED 저장 → PG approve → Payment.APPROVED + Order.markPaid + OutboxEvent insert 흐름이 수행된다") {
                val order = sampleOrder(id = 1L)
                val approvedAt = LocalDateTime.of(2026, 5, 8, 10, 0)
                val requestedPayment = Payment(
                    id = 99L,
                    orderId = 1L,
                    idempotencyKey = idempotencyKey,
                    amount = order.totalAmount,
                    status = PaymentStatus.REQUESTED
                )

                every { orderLookupService.requireById(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
                every { paymentLifecycleService.createRequested(1L, idempotencyKey, order.totalAmount) } returns requestedPayment
                every { paymentGateway.approve(order.totalAmount, idempotencyKey) } returns
                    ApproveResult(paymentKey = "MOCK-PG-test-key", approvedAt = approvedAt)
                every { paymentLifecycleService.markApproved(99L, "MOCK-PG-test-key", approvedAt) } returns requestedPayment
                every { outboxEventRepository.save(any<OutboxEvent>()) } answers { firstArg() }

                val result = orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))

                result.status shouldBe OrderStatus.PAID
                result.paidAt shouldBe approvedAt
                result.paymentKey shouldBe "MOCK-PG-test-key"
                verify(exactly = 1) { paymentRepository.findByIdempotencyKey(idempotencyKey) }
                verify(exactly = 1) { paymentLifecycleService.createRequested(1L, idempotencyKey, order.totalAmount) }
                verify(exactly = 1) { paymentGateway.approve(order.totalAmount, idempotencyKey) }
                verify(exactly = 1) { paymentLifecycleService.markApproved(99L, "MOCK-PG-test-key", approvedAt) }
                verify(exactly = 1) { outboxEventRepository.save(any<OutboxEvent>()) }
            }
        }

        When("같은 idempotencyKey + 같은 order + 같은 amount로 재요청하면") {
            Then("PG approve 재호출 없이 기존 paymentKey를 그대로 replay한다") {
                val order = sampleOrder(id = 1L)
                val existing = Payment(
                    id = 99L,
                    orderId = 1L,
                    idempotencyKey = idempotencyKey,
                    amount = order.totalAmount,
                    status = PaymentStatus.APPROVED,
                    paymentKey = "MOCK-PG-existing-key",
                    approvedAt = LocalDateTime.of(2026, 5, 8, 9, 0)
                )

                every { orderLookupService.requireById(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns existing

                val result = orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))

                result.paymentKey shouldBe "MOCK-PG-existing-key"
                verify(exactly = 0) { paymentGateway.approve(any(), any()) }
                verify(exactly = 0) { paymentRepository.save(any<Payment>()) }
            }
        }

        When("같은 idempotencyKey가 다른 order에 이미 사용되었으면") {
            Then("IdempotencyConflictException을 던진다") {
                val order = sampleOrder(id = 1L)
                val existing = Payment(
                    id = 99L,
                    orderId = 999L, // 다른 order
                    idempotencyKey = idempotencyKey,
                    amount = order.totalAmount,
                    status = PaymentStatus.APPROVED,
                    paymentKey = "MOCK-PG-other"
                )

                every { orderLookupService.requireById(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns existing

                shouldThrow<IdempotencyConflictException> {
                    orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))
                }

                verify(exactly = 0) { paymentGateway.approve(any(), any()) }
            }
        }

        When("같은 idempotencyKey + 같은 order인데 amount가 다르면") {
            Then("IdempotencyConflictException을 던진다") {
                val order = sampleOrder(id = 1L)
                val existing = Payment(
                    id = 99L,
                    orderId = 1L,
                    idempotencyKey = idempotencyKey,
                    amount = order.totalAmount.add(java.math.BigDecimal("1.00")), // 다른 amount
                    status = PaymentStatus.APPROVED,
                    paymentKey = "MOCK-PG-other"
                )

                every { orderLookupService.requireById(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns existing

                shouldThrow<IdempotencyConflictException> {
                    orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))
                }
            }
        }

        When("이미 결제된 주문이면") {
            Then("PG approve 호출 없이 상태 전이 예외를 던진다") {
                val order = sampleOrder(id = 1L).apply {
                    markPaid(LocalDateTime.of(2026, 5, 8, 10, 0))
                }

                every { orderLookupService.requireById(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null

                val exception = shouldThrow<InvalidOrderStatusTransitionException> {
                    orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))
                }

                exception.message shouldBe "only created orders can be paid. current status: PAID"
                verify(exactly = 0) { paymentGateway.approve(any(), any()) }
            }
        }

        When("PG approve가 PaymentApprovalFailedException을 던지면") {
            Then("시나리오 A — PaymentLifecycleService.markFailed가 호출되고 Order 상태가 CREATED로 유지되며 예외가 전파된다 (보상 호출 없음)") {
                val order = sampleOrder(id = 1L)
                val requestedPayment = Payment(
                    id = 99L,
                    orderId = 1L,
                    idempotencyKey = idempotencyKey,
                    amount = order.totalAmount,
                    status = PaymentStatus.REQUESTED
                )

                every { orderLookupService.requireById(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
                every { paymentLifecycleService.createRequested(1L, idempotencyKey, order.totalAmount) } returns requestedPayment
                every { paymentLifecycleService.markFailed(99L, any(), any()) } returns Unit
                every { paymentGateway.approve(order.totalAmount, idempotencyKey) } throws
                    PaymentApprovalFailedException("PG declined: insufficient balance")

                shouldThrow<PaymentApprovalFailedException> {
                    orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))
                }

                order.status shouldBe OrderStatus.CREATED
                order.paidAt.shouldBeNull()
                verify(exactly = 1) { paymentLifecycleService.markFailed(99L, any(), any()) }
                verify(exactly = 0) { paymentLifecycleService.markApproved(any(), any(), any()) }
                verify(exactly = 0) {
                    compensationService.compensateApprovedPayment(
                        paymentId = any(), paymentKey = any(), amount = any(), reason = any(), now = any()
                    )
                }
            }
        }

        When("approve는 성공했지만 outbox insert에서 실패하면") {
            Then("시나리오 B-1 — CompensationService.compensateApprovedPayment가 REFUNDED outcome으로 호출되고 원래 예외가 전파된다") {
                val order = sampleOrder(id = 1L)
                val approvedAt = LocalDateTime.of(2026, 5, 8, 10, 0)
                val requestedPayment = Payment(
                    id = 77L,
                    orderId = 1L,
                    idempotencyKey = idempotencyKey,
                    amount = order.totalAmount,
                    status = PaymentStatus.REQUESTED
                )

                every { orderLookupService.requireById(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
                every { paymentLifecycleService.createRequested(1L, idempotencyKey, order.totalAmount) } returns requestedPayment
                every { paymentGateway.approve(order.totalAmount, idempotencyKey) } returns
                    ApproveResult(paymentKey = "MOCK-PG-b1", approvedAt = approvedAt)
                every { paymentLifecycleService.markApproved(77L, "MOCK-PG-b1", approvedAt) } returns requestedPayment
                every { outboxEventRepository.save(any<OutboxEvent>()) } throws
                    RuntimeException("simulated outbox failure")
                every {
                    compensationService.compensateApprovedPayment(
                        paymentId = 77L,
                        paymentKey = "MOCK-PG-b1",
                        amount = any(),
                        reason = any(),
                        now = any()
                    )
                } returns CompensationOutcome.Refunded(approvedAt.plusSeconds(1))

                shouldThrow<RuntimeException> {
                    orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))
                }.message shouldBe "simulated outbox failure"

                verify(exactly = 1) {
                    compensationService.compensateApprovedPayment(
                        paymentId = 77L,
                        paymentKey = "MOCK-PG-b1",
                        amount = any(),
                        reason = any(),
                        now = any()
                    )
                }
            }
        }

        When("approve는 성공했지만 outbox insert + PG refund도 실패하면") {
            Then("시나리오 B-2 — compensateApprovedPayment가 SCHEDULED outcome으로 호출되고 (CompensationTask 등록은 service 내부) 원래 예외가 전파된다") {
                val order = sampleOrder(id = 1L)
                val approvedAt = LocalDateTime.of(2026, 5, 8, 10, 0)
                val requestedPayment = Payment(
                    id = 88L,
                    orderId = 1L,
                    idempotencyKey = idempotencyKey,
                    amount = order.totalAmount,
                    status = PaymentStatus.REQUESTED
                )

                every { orderLookupService.requireById(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
                every { paymentLifecycleService.createRequested(1L, idempotencyKey, order.totalAmount) } returns requestedPayment
                every { paymentGateway.approve(order.totalAmount, idempotencyKey) } returns
                    ApproveResult(paymentKey = "MOCK-PG-b2", approvedAt = approvedAt)
                every { paymentLifecycleService.markApproved(88L, "MOCK-PG-b2", approvedAt) } returns requestedPayment
                every { outboxEventRepository.save(any<OutboxEvent>()) } throws
                    RuntimeException("simulated outbox failure")
                every {
                    compensationService.compensateApprovedPayment(
                        paymentId = 88L,
                        paymentKey = "MOCK-PG-b2",
                        amount = any(),
                        reason = any(),
                        now = any()
                    )
                } returns CompensationOutcome.Scheduled(taskId = 42L)

                shouldThrow<RuntimeException> {
                    orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))
                }

                verify(exactly = 1) {
                    compensationService.compensateApprovedPayment(
                        paymentId = 88L,
                        paymentKey = "MOCK-PG-b2",
                        amount = any(),
                        reason = any(),
                        now = any()
                    )
                }
            }
        }
    }

    Given("주문 배송 요청이 들어오면") {
        When("주문이 PAID 상태면") {
            Then("SHIPPED 상태와 배송 시각을 반영한다") {
                val order = sampleOrder(id = 2L).apply {
                    markPaid(LocalDateTime.of(2026, 5, 7, 9, 0))
                }

                every { orderLookupService.requireById(2L) } returns order

                val result = orderUseCase.shipOrder(ShipOrderCommand(2L))

                result.status shouldBe OrderStatus.SHIPPED
                result.paidAt shouldBe LocalDateTime.of(2026, 5, 7, 9, 0)
                result.shippedAt.shouldNotBeNull()
                verify(exactly = 1) { orderLookupService.requireById(2L) }
            }
        }
    }

    Given("주문 취소 요청이 들어오면") {
        When("이미 배송된 주문이면") {
            Then("취소할 수 없다는 예외를 던진다") {
                val order = sampleOrder(id = 3L).apply {
                    markPaid(LocalDateTime.of(2026, 5, 6, 9, 0))
                    markShipped(LocalDateTime.of(2026, 5, 7, 14, 0))
                }

                every { orderLookupService.requireById(3L) } returns order
                every { cancellationRepository.findByIdempotencyKey("test-cancel-key-1") } returns null

                val exception = shouldThrow<InvalidOrderStatusTransitionException> {
                    orderUseCase.cancelOrder(CancelOrderCommand(3L, "test-cancel-key-1"))
                }

                exception.message shouldBe "only created or paid orders can be cancelled. current status: SHIPPED"
                verify(exactly = 1) { orderLookupService.requireById(3L) }
            }
        }
    }

    Given("주문 목록 조회를 요청하면") {
        When("searchMode가 DERIVED이고 status 조건이 있으면") {
            Then("상태 조건용 파생 쿼리를 사용한다") {
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                val order = sampleOrder(id = 10L).apply {
                    markPaid(LocalDateTime.of(2026, 5, 8, 9, 0))
                }

                every {
                    orderRepository.findAllByDeletedAtIsNullAndStatus(OrderStatus.PAID, pageable)
                } returns PageImpl(listOf(order), pageable, 1)

                val result = orderUseCase.getOrders(
                    FindOrdersCommand(
                        page = 0,
                        size = 10,
                        status = OrderStatus.PAID,
                        searchMode = OrderSearchMode.DERIVED
                    )
                )

                result.content shouldHaveSize 1
                result.content.first().status shouldBe OrderStatus.PAID
                verify(exactly = 1) {
                    orderRepository.findAllByDeletedAtIsNullAndStatus(OrderStatus.PAID, pageable)
                }
            }
        }

        When("searchMode가 JPQL이고 customerName, status 조건이 함께 있으면") {
            Then("nullable 조건식 기반 JPQL 조회를 사용한다") {
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                val order = sampleOrder(id = 11L, customerName = "한수진").apply {
                    markPaid(LocalDateTime.of(2026, 5, 8, 9, 30))
                }

                every {
                    orderRepository.searchByConditions("한수진", OrderStatus.PAID, pageable)
                } returns PageImpl(listOf(order), pageable, 1)

                val result = orderUseCase.getOrders(
                    FindOrdersCommand(
                        page = 0,
                        size = 10,
                        customerName = "한수진",
                        status = OrderStatus.PAID,
                        searchMode = OrderSearchMode.JPQL
                    )
                )

                result.content shouldHaveSize 1
                result.content.first().customerName shouldBe "한수진"
                verify(exactly = 1) {
                    orderRepository.searchByConditions("한수진", OrderStatus.PAID, pageable)
                }
            }
        }
    }

    Given("주문 상태 요약을 조회하면") {
        When("customerName, status 조건이 함께 있으면") {
            Then("필터가 적용된 projection 결과를 application result로 변환한다") {
                every { orderRepository.findStatusSummaries("한수진", OrderStatus.PAID) } returns listOf(
                    statusSummaryProjection(OrderStatus.PAID, 1)
                )

                val result = orderUseCase.getOrderStatusSummaries(
                    FindOrderStatusSummariesCommand(
                        customerName = "한수진",
                        status = OrderStatus.PAID
                    )
                )

                result shouldHaveSize 1
                result[0].status shouldBe OrderStatus.PAID
                result[0].count shouldBe 1
                verify(exactly = 1) { orderRepository.findStatusSummaries("한수진", OrderStatus.PAID) }
            }
        }
    }
})

private fun sampleOrder(id: Long, customerName: String = "한수진"): Order =
    Order(
        id = id,
        customer = Customer(id = 1L, name = customerName, email = "$customerName@example.com"),
        orderNo = "ORD-2024-$id",
        shippingAddress = ShippingAddress(
            recipient = customerName,
            zipCode = "06236",
            address1 = "Seoul Gangnam-daero 1",
            address2 = "101-ho"
        )
    )

private fun statusSummaryProjection(
    orderStatus: OrderStatus,
    orderCount: Long
): OrderStatusSummaryProjection = object : OrderStatusSummaryProjection {
    override val status: OrderStatus = orderStatus
    override val count: Long = orderCount
}
