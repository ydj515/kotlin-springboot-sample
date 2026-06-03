package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.compensation.CompensationOutcome
import com.example.kotlinspringbootsample.application.compensation.CompensationService
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
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.order.repository.projection.OrderStatusSummaryProjection
import com.example.kotlinspringbootsample.domain.order.service.OrderLookupService
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentApprovalFailedException
import com.example.kotlinspringbootsample.domain.payment.gateway.ApproveResult
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.time.LocalDateTime

class OrderUseCaseTest : BehaviorSpec({

    val orderRepository = mockk<OrderRepository>()
    val customerLookupService = mockk<CustomerLookupService>()
    val orderLookupService = mockk<OrderLookupService>()
    val paymentGateway = mockk<PaymentGateway>()
    val compensationService = mockk<CompensationService>()
    val orderPaymentTransactionService = mockk<OrderPaymentTransactionService>()
    val orderUseCase = OrderUseCase(
        orderRepository = orderRepository,
        customerLookupService = customerLookupService,
        orderLookupService = orderLookupService,
        orderItemPolicy = OrderItemPolicy(),
        orderStatusTransitionPolicy = OrderStatusTransitionPolicy(),
        paymentGateway = paymentGateway,
        compensationService = compensationService,
        orderPaymentTransactionService = orderPaymentTransactionService
    )

    beforeTest {
        clearMocks(
            orderRepository,
            customerLookupService,
            orderLookupService,
            paymentGateway,
            compensationService,
            orderPaymentTransactionService
        )
    }

    Given("주문 결제 요청이 들어오면") {
        val idempotencyKey = "550e8400-e29b-41d4-a716-446655440000"

        When("신규 결제이고 PG approve와 주문 완료 DB 반영이 모두 성공하면") {
            Then("결제 준비 트랜잭션 이후 PG approve를 호출하고 승인 기록과 주문 완료를 순서대로 위임한다") {
                val approvedAt = LocalDateTime.of(2026, 5, 8, 10, 0)
                val amount = BigDecimal("10000.00")
                val preparation = PayOrderPreparation.ApprovalRequired(
                    orderId = 1L,
                    paymentId = 99L,
                    amount = amount,
                    idempotencyKey = idempotencyKey
                )
                val result = paidOrderResult(id = 1L, amount = amount, approvedAt = approvedAt)
                    .copy(paymentKey = "MOCK-PG-test-key")

                every { orderPaymentTransactionService.preparePayOrder(PayOrderCommand(1L, idempotencyKey)) } returns
                    preparation
                every { paymentGateway.approve(amount, idempotencyKey) } returns
                    ApproveResult(paymentKey = "MOCK-PG-test-key", approvedAt = approvedAt)
                every { orderPaymentTransactionService.markPaymentApproved(99L, "MOCK-PG-test-key", approvedAt) } returns
                    Unit
                every {
                    orderPaymentTransactionService.completePayOrder(1L, 99L, "MOCK-PG-test-key", approvedAt)
                } returns result

                val actual = orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))

                actual.status shouldBe OrderStatus.PAID
                actual.paymentKey shouldBe "MOCK-PG-test-key"
                verify(exactly = 1) { paymentGateway.approve(amount, idempotencyKey) }
                verify(exactly = 1) { orderPaymentTransactionService.markPaymentApproved(99L, "MOCK-PG-test-key", approvedAt) }
                verify(exactly = 1) { orderPaymentTransactionService.completePayOrder(1L, 99L, "MOCK-PG-test-key", approvedAt) }
            }
        }

        When("같은 idempotencyKey 요청이 replay 대상이면") {
            Then("PG approve를 다시 호출하지 않고 준비 단계의 결과를 반환한다") {
                val result = paidOrderResult(id = 1L).copy(paymentKey = "MOCK-PG-existing-key")

                every { orderPaymentTransactionService.preparePayOrder(PayOrderCommand(1L, idempotencyKey)) } returns
                    PayOrderPreparation.Replay(result)

                val actual = orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))

                actual.paymentKey shouldBe "MOCK-PG-existing-key"
                verify(exactly = 0) { paymentGateway.approve(any(), any()) }
                verify(exactly = 0) { orderPaymentTransactionService.markPaymentApproved(any(), any(), any()) }
                verify(exactly = 0) { orderPaymentTransactionService.completePayOrder(any(), any(), any(), any()) }
            }
        }

        When("결제 준비 단계에서 주문 상태 전이 예외가 발생하면") {
            Then("PG approve 호출 없이 예외를 전파한다") {
                every { orderPaymentTransactionService.preparePayOrder(PayOrderCommand(1L, idempotencyKey)) } throws
                    InvalidOrderStatusTransitionException("only created orders can be paid. current status: PAID")

                val exception = shouldThrow<InvalidOrderStatusTransitionException> {
                    orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))
                }

                exception.message shouldBe "only created orders can be paid. current status: PAID"
                verify(exactly = 0) { paymentGateway.approve(any(), any()) }
            }
        }

        When("PG approve가 명시적 결제 실패 예외를 던지면") {
            Then("Payment FAILED 기록만 위임하고 환불 보상은 호출하지 않는다") {
                val preparation = PayOrderPreparation.ApprovalRequired(
                    orderId = 1L,
                    paymentId = 99L,
                    amount = BigDecimal("10000.00"),
                    idempotencyKey = idempotencyKey
                )

                every { orderPaymentTransactionService.preparePayOrder(PayOrderCommand(1L, idempotencyKey)) } returns
                    preparation
                every { paymentGateway.approve(preparation.amount, idempotencyKey) } throws
                    PaymentApprovalFailedException("PG declined: insufficient balance")
                every { orderPaymentTransactionService.markPaymentFailed(99L, any(), any()) } returns Unit

                shouldThrow<PaymentApprovalFailedException> {
                    orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))
                }

                verify(exactly = 1) { orderPaymentTransactionService.markPaymentFailed(99L, any(), any()) }
                verify(exactly = 0) { compensationService.compensateApprovedPayment(any(), any(), any(), any(), any()) }
            }
        }

        When("PG approve 후 승인 기록 저장이 실패하면") {
            Then("승인된 외부 결제를 환불 보상하고 원래 예외를 전파한다") {
                val approvedAt = LocalDateTime.of(2026, 5, 8, 10, 0)
                val preparation = PayOrderPreparation.ApprovalRequired(
                    orderId = 1L,
                    paymentId = 77L,
                    amount = BigDecimal("10000.00"),
                    idempotencyKey = idempotencyKey
                )

                every { orderPaymentTransactionService.preparePayOrder(PayOrderCommand(1L, idempotencyKey)) } returns
                    preparation
                every { paymentGateway.approve(preparation.amount, idempotencyKey) } returns
                    ApproveResult(paymentKey = "MOCK-PG-persist-fail", approvedAt = approvedAt)
                every {
                    orderPaymentTransactionService.markPaymentApproved(77L, "MOCK-PG-persist-fail", approvedAt)
                } throws RuntimeException("approval persistence failed")
                every {
                    compensationService.compensateApprovedPayment(
                        paymentId = 77L,
                        paymentKey = "MOCK-PG-persist-fail",
                        amount = preparation.amount,
                        reason = any(),
                        now = any()
                    )
                } returns CompensationOutcome.Refunded(approvedAt.plusSeconds(1))

                shouldThrow<RuntimeException> {
                    orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))
                }.message shouldBe "approval persistence failed"

                verify(exactly = 1) {
                    compensationService.compensateApprovedPayment(
                        paymentId = 77L,
                        paymentKey = "MOCK-PG-persist-fail",
                        amount = preparation.amount,
                        reason = any(),
                        now = any()
                    )
                }
            }
        }

        When("PG approve와 승인 기록은 성공했지만 주문 완료 트랜잭션이 실패하면") {
            Then("환불 보상을 호출하고 원래 예외를 전파한다") {
                val approvedAt = LocalDateTime.of(2026, 5, 8, 10, 0)
                val preparation = PayOrderPreparation.ApprovalRequired(
                    orderId = 1L,
                    paymentId = 88L,
                    amount = BigDecimal("10000.00"),
                    idempotencyKey = idempotencyKey
                )

                every { orderPaymentTransactionService.preparePayOrder(PayOrderCommand(1L, idempotencyKey)) } returns
                    preparation
                every { paymentGateway.approve(preparation.amount, idempotencyKey) } returns
                    ApproveResult(paymentKey = "MOCK-PG-b2", approvedAt = approvedAt)
                every { orderPaymentTransactionService.markPaymentApproved(88L, "MOCK-PG-b2", approvedAt) } returns Unit
                every {
                    orderPaymentTransactionService.completePayOrder(1L, 88L, "MOCK-PG-b2", approvedAt)
                } throws RuntimeException("simulated outbox failure")
                every {
                    compensationService.compensateApprovedPayment(
                        paymentId = 88L,
                        paymentKey = "MOCK-PG-b2",
                        amount = preparation.amount,
                        reason = any(),
                        now = any()
                    )
                } returns CompensationOutcome.Scheduled(taskId = 42L)

                shouldThrow<RuntimeException> {
                    orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey))
                }.message shouldBe "simulated outbox failure"

                verify(exactly = 1) {
                    compensationService.compensateApprovedPayment(
                        paymentId = 88L,
                        paymentKey = "MOCK-PG-b2",
                        amount = preparation.amount,
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
            Then("환불 호출 없이 준비 단계 예외를 전파한다") {
                every { orderPaymentTransactionService.prepareCancelOrder(CancelOrderCommand(3L, "test-cancel-key-1")) } throws
                    InvalidOrderStatusTransitionException("only created or paid orders can be cancelled. current status: SHIPPED")

                val exception = shouldThrow<InvalidOrderStatusTransitionException> {
                    orderUseCase.cancelOrder(CancelOrderCommand(3L, "test-cancel-key-1"))
                }

                exception.message shouldBe "only created or paid orders can be cancelled. current status: SHIPPED"
                verify(exactly = 0) { paymentGateway.refund(any(), any()) }
            }
        }

        When("PAID 주문 취소라 환불이 필요하면") {
            Then("환불 보상 호출 후 cancellation 결과 기록을 위임한다") {
                val result = cancelledOrderResult(id = 3L)
                val preparation = CancelOrderPreparation.RefundRequired(
                    result = result,
                    cancellationId = 7L,
                    paymentId = 99L,
                    paymentKey = "MOCK-PG-cancel",
                    amount = BigDecimal("10000.00"),
                    reason = "order cancel: 고객 요청"
                )
                val outcome = CompensationOutcome.Refunded(LocalDateTime.of(2026, 5, 8, 11, 0))

                every { orderPaymentTransactionService.prepareCancelOrder(CancelOrderCommand(3L, "cancel-key", "고객 요청")) } returns
                    preparation
                every {
                    compensationService.compensateApprovedPayment(99L, "MOCK-PG-cancel", preparation.amount, preparation.reason, any())
                } returns outcome
                every { orderPaymentTransactionService.recordCancellationRefundOutcome(7L, outcome) } returns Unit

                val actual = orderUseCase.cancelOrder(CancelOrderCommand(3L, "cancel-key", "고객 요청"))

                actual.status shouldBe OrderStatus.CANCELLED
                verify(exactly = 1) {
                    compensationService.compensateApprovedPayment(99L, "MOCK-PG-cancel", preparation.amount, preparation.reason, any())
                }
                verify(exactly = 1) { orderPaymentTransactionService.recordCancellationRefundOutcome(7L, outcome) }
            }
        }

        When("replay 또는 미결제 취소처럼 추가 외부 호출이 필요 없으면") {
            Then("준비 단계의 결과를 그대로 반환한다") {
                val result = cancelledOrderResult(id = 4L)

                every { orderPaymentTransactionService.prepareCancelOrder(CancelOrderCommand(4L, "cancel-key")) } returns
                    CancelOrderPreparation.Completed(result)

                val actual = orderUseCase.cancelOrder(CancelOrderCommand(4L, "cancel-key"))

                actual.status shouldBe OrderStatus.CANCELLED
                verify(exactly = 0) { compensationService.compensateApprovedPayment(any(), any(), any(), any(), any()) }
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

private fun paidOrderResult(
    id: Long,
    amount: BigDecimal = BigDecimal("10000.00"),
    approvedAt: LocalDateTime = LocalDateTime.of(2026, 5, 8, 10, 0)
) = sampleOrder(id = id, amount = amount).apply {
    markPaid(approvedAt)
}.toResult()

private fun cancelledOrderResult(id: Long): com.example.kotlinspringbootsample.application.order.result.OrderResult =
    sampleOrder(id = id).apply {
        cancel(reason = "고객 요청")
    }.toResult()

private fun sampleOrder(
    id: Long,
    customerName: String = "한수진",
    amount: BigDecimal = BigDecimal("10000.00")
): Order =
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
    ).apply {
        replaceLines(
            listOf(
                com.example.kotlinspringbootsample.domain.order.OrderLineDraft(
                    productName = "TestItem",
                    quantity = 1,
                    unitPrice = amount
                )
            )
        )
    }

private fun statusSummaryProjection(
    orderStatus: OrderStatus,
    orderCount: Long
): OrderStatusSummaryProjection = object : OrderStatusSummaryProjection {
    override val status: OrderStatus = orderStatus
    override val count: Long = orderCount
}
