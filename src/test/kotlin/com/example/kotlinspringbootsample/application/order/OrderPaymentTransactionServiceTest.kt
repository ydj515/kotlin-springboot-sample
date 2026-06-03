package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.order.command.CancelOrderCommand
import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.domain.customer.Customer
import com.example.kotlinspringbootsample.domain.order.Cancellation
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.ShippingAddress
import com.example.kotlinspringbootsample.domain.order.policy.OrderStatusTransitionPolicy
import com.example.kotlinspringbootsample.domain.order.repository.CancellationRepository
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.outbox.OutboxEvent
import com.example.kotlinspringbootsample.domain.outbox.repository.OutboxEventRepository
import com.example.kotlinspringbootsample.domain.payment.Payment
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.exception.IdempotencyConflictException
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

class OrderPaymentTransactionServiceTest : BehaviorSpec({

    val orderRepository = mockk<OrderRepository>()
    val paymentRepository = mockk<PaymentRepository>()
    val outboxEventRepository = mockk<OutboxEventRepository>()
    val cancellationRepository = mockk<CancellationRepository>()
    val service = OrderPaymentTransactionService(
        orderRepository = orderRepository,
        orderStatusTransitionPolicy = OrderStatusTransitionPolicy(),
        paymentRepository = paymentRepository,
        outboxEventRepository = outboxEventRepository,
        objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule()),
        cancellationRepository = cancellationRepository
    )

    beforeTest {
        clearMocks(orderRepository, paymentRepository, outboxEventRepository, cancellationRepository)
    }

    Given("payOrder 준비 트랜잭션") {
        val idempotencyKey = "pay-key"

        When("신규 결제 요청이고 주문이 CREATED 상태이면") {
            Then("Payment.REQUESTED를 저장하고 PG 승인에 필요한 스냅샷을 반환한다") {
                val order = sampleOrder(id = 1L, amount = BigDecimal("15000.00"))

                every { orderRepository.findByIdAndDeletedAtIsNullForUpdate(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
                every { paymentRepository.findByOrderId(1L) } returns emptyList()
                every { paymentRepository.save(any<Payment>()) } answers {
                    firstArg<Payment>().apply { id = 99L }
                }

                val result = service.preparePayOrder(PayOrderCommand(1L, idempotencyKey))

                result as PayOrderPreparation.ApprovalRequired
                result.orderId shouldBe 1L
                result.paymentId shouldBe 99L
                result.amount shouldBe BigDecimal("15000.00")
                verify(exactly = 1) {
                    paymentRepository.save(
                        match {
                            it.orderId == 1L &&
                                it.idempotencyKey == idempotencyKey &&
                                it.status == PaymentStatus.REQUESTED
                        }
                    )
                }
            }
        }

        When("같은 idempotencyKey의 APPROVED payment가 있으면") {
            Then("PG 재호출 대상이 아닌 replay 결과를 반환한다") {
                val order = sampleOrder(id = 1L, amount = BigDecimal("15000.00")).apply {
                    markPaid(LocalDateTime.of(2026, 5, 8, 10, 0))
                }
                val existing = Payment(
                    id = 99L,
                    orderId = 1L,
                    idempotencyKey = idempotencyKey,
                    amount = BigDecimal("15000.00"),
                    status = PaymentStatus.APPROVED,
                    paymentKey = "MOCK-PG-existing"
                )

                every { orderRepository.findByIdAndDeletedAtIsNullForUpdate(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns existing

                val result = service.preparePayOrder(PayOrderCommand(1L, idempotencyKey))

                result as PayOrderPreparation.Replay
                result.result.paymentKey shouldBe "MOCK-PG-existing"
                verify(exactly = 0) { paymentRepository.save(any<Payment>()) }
            }
        }

        When("다른 idempotencyKey로 이미 진행 중인 결제가 있으면") {
            Then("새 Payment를 만들지 않고 충돌 예외를 던진다") {
                val order = sampleOrder(id = 1L, amount = BigDecimal("15000.00"))
                val activePayment = Payment(
                    id = 100L,
                    orderId = 1L,
                    idempotencyKey = "other-pay-key",
                    amount = BigDecimal("15000.00"),
                    status = PaymentStatus.REQUESTED
                )

                every { orderRepository.findByIdAndDeletedAtIsNullForUpdate(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
                every { paymentRepository.findByOrderId(1L) } returns listOf(activePayment)

                shouldThrow<IdempotencyConflictException> {
                    service.preparePayOrder(PayOrderCommand(1L, idempotencyKey))
                }
                verify(exactly = 0) { paymentRepository.save(any<Payment>()) }
            }
        }

        When("같은 idempotencyKey가 다른 주문에 이미 사용되었으면") {
            Then("IdempotencyConflictException을 던진다") {
                val order = sampleOrder(id = 1L, amount = BigDecimal("15000.00"))
                val existing = Payment(
                    id = 99L,
                    orderId = 2L,
                    idempotencyKey = idempotencyKey,
                    amount = BigDecimal("15000.00"),
                    status = PaymentStatus.APPROVED,
                    paymentKey = "MOCK-PG-other"
                )

                every { orderRepository.findByIdAndDeletedAtIsNullForUpdate(1L) } returns order
                every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns existing

                shouldThrow<IdempotencyConflictException> {
                    service.preparePayOrder(PayOrderCommand(1L, idempotencyKey))
                }
            }
        }
    }

    Given("payOrder 완료 트랜잭션") {
        When("Payment.APPROVED가 기록되어 있으면") {
            Then("Order를 PAID로 바꾸고 OrderPaidEvent outbox를 같은 트랜잭션에 저장한다") {
                val approvedAt = LocalDateTime.of(2026, 5, 8, 10, 0)
                val order = sampleOrder(id = 1L, amount = BigDecimal("15000.00"))
                val payment = Payment(
                    id = 99L,
                    orderId = 1L,
                    idempotencyKey = "pay-key",
                    amount = BigDecimal("15000.00"),
                    status = PaymentStatus.APPROVED,
                    paymentKey = "MOCK-PG-approved",
                    approvedAt = approvedAt
                )
                val outboxSlot = slot<OutboxEvent>()

                every { orderRepository.findByIdAndDeletedAtIsNullForUpdate(1L) } returns order
                every { paymentRepository.findById(99L) } returns Optional.of(payment)
                every { outboxEventRepository.save(capture(outboxSlot)) } answers { firstArg() }

                val result = service.completePayOrder(
                    orderId = 1L,
                    paymentId = 99L,
                    paymentKey = "MOCK-PG-approved",
                    approvedAt = approvedAt
                )

                result.status shouldBe OrderStatus.PAID
                result.paidAt shouldBe approvedAt
                result.paymentKey shouldBe "MOCK-PG-approved"
                outboxSlot.captured.aggregateType shouldBe "Order"
                outboxSlot.captured.eventType shouldBe "OrderPaidEvent"
            }
        }
    }

    Given("cancelOrder 준비 트랜잭션") {
        When("PAID 주문이면") {
            Then("주문과 cancellation을 먼저 기록하고 환불 호출에 필요한 스냅샷을 반환한다") {
                val order = sampleOrder(id = 3L, amount = BigDecimal("12000.00")).apply {
                    markPaid(LocalDateTime.of(2026, 5, 8, 9, 0))
                }
                val cancellation = Cancellation.requested(3L, "cancel-key", "고객 요청").apply { id = 7L }
                val payment = Payment(
                    id = 99L,
                    orderId = 3L,
                    idempotencyKey = "pay-key",
                    amount = BigDecimal("12000.00"),
                    status = PaymentStatus.APPROVED,
                    paymentKey = "MOCK-PG-paid"
                )

                every { orderRepository.findByIdAndDeletedAtIsNullForUpdate(3L) } returns order
                every { cancellationRepository.findByIdempotencyKey("cancel-key") } returns null
                every { cancellationRepository.save(any<Cancellation>()) } returns cancellation
                every { paymentRepository.findByOrderId(3L) } returns listOf(payment)

                val result = service.prepareCancelOrder(CancelOrderCommand(3L, "cancel-key", "고객 요청"))

                result as CancelOrderPreparation.RefundRequired
                result.cancellationId shouldBe 7L
                result.paymentId shouldBe 99L
                result.paymentKey shouldBe "MOCK-PG-paid"
                result.result.status shouldBe OrderStatus.CANCELLED
            }
        }
    }
})

private fun sampleOrder(
    id: Long,
    amount: BigDecimal
): Order =
    Order(
        id = id,
        customer = Customer(id = 1L, name = "한수진", email = "user@example.com"),
        orderNo = "ORD-2024-$id",
        shippingAddress = ShippingAddress(
            recipient = "한수진",
            zipCode = "06236",
            address1 = "Seoul Gangnam-daero 1",
            address2 = "101-ho"
        )
    ).apply {
        replaceLines(listOf(OrderLineDraft(productName = "TestItem", quantity = 1, unitPrice = amount)))
    }
