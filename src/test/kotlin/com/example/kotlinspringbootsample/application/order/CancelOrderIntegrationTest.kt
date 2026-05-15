package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.order.command.CancelOrderCommand
import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.domain.compensation.CompensationTaskStatus
import com.example.kotlinspringbootsample.domain.compensation.repository.CompensationTaskRepository
import com.example.kotlinspringbootsample.domain.customer.Customer
import com.example.kotlinspringbootsample.domain.customer.repository.CustomerRepository
import com.example.kotlinspringbootsample.domain.order.CancellationStatus
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.ShippingAddress
import com.example.kotlinspringbootsample.domain.order.repository.CancellationRepository
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.exception.IdempotencyConflictException
import com.example.kotlinspringbootsample.domain.payment.gateway.ApproveResult
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import com.example.kotlinspringbootsample.domain.payment.gateway.RefundResult
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
import com.example.kotlinspringbootsample.support.MySqlIntegrationTestSupport
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class CancelOrderIntegrationTest @Autowired constructor(
    private val orderUseCase: OrderUseCase,
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val cancellationRepository: CancellationRepository,
    private val compensationTaskRepository: CompensationTaskRepository,
    @MockkBean private val paymentGateway: PaymentGateway
) : MySqlIntegrationTestSupport() {

    @AfterEach
    fun cleanup() {
        compensationTaskRepository.deleteAll()
        cancellationRepository.deleteAll()
        paymentRepository.deleteAll()
        orderRepository.deleteAll()
        customerRepository.deleteAll()
    }

    @Test
    fun `시나리오 C-1 - cancel(PAID) refund 성공시 Order CANCELLED + Payment REFUNDED + cancellation SUCCEEDED`() {
        val orderId = setupPaidOrder(BigDecimal("10000.00"))
        val payment = paymentRepository.findAll().first()
        val cancelKey = "cancel-c1-${UUID.randomUUID()}"

        every { paymentGateway.refund(payment.paymentKey!!, payment.amount) } returns
            RefundResult(refundedAt = LocalDateTime.now().withNano(0))

        val result = orderUseCase.cancelOrder(CancelOrderCommand(orderId, cancelKey, reason = "고객 요청"))

        assertThat(result.status).isEqualTo(OrderStatus.CANCELLED)
        val refreshedPayment = paymentRepository.findById(payment.id!!).orElseThrow()
        assertThat(refreshedPayment.status).isEqualTo(PaymentStatus.REFUNDED)
        val cancellation = cancellationRepository.findByIdempotencyKey(cancelKey)!!
        assertThat(cancellation.status).isEqualTo(CancellationStatus.SUCCEEDED)
        assertThat(cancellation.refundedAt).isNotNull
        assertThat(compensationTaskRepository.count()).isZero
    }

    @Test
    fun `시나리오 C-2 - cancel(PAID) refund 실패시 Order CANCELLED + Payment REFUND_FAILED + cancellation REFUND_FAILED + CompensationTask PENDING`() {
        val orderId = setupPaidOrder(BigDecimal("12000.00"))
        val payment = paymentRepository.findAll().first()
        val cancelKey = "cancel-c2-${UUID.randomUUID()}"

        every { paymentGateway.refund(payment.paymentKey!!, payment.amount) } throws
            RuntimeException("simulated PG refund failure")

        orderUseCase.cancelOrder(CancelOrderCommand(orderId, cancelKey, reason = "테스트"))

        val refreshedPayment = paymentRepository.findById(payment.id!!).orElseThrow()
        assertThat(refreshedPayment.status).isEqualTo(PaymentStatus.REFUND_FAILED)
        val cancellation = cancellationRepository.findByIdempotencyKey(cancelKey)!!
        assertThat(cancellation.status).isEqualTo(CancellationStatus.REFUND_FAILED)

        val tasks = compensationTaskRepository.findAll()
        assertThat(tasks).hasSize(1)
        assertThat(tasks.first().status).isEqualTo(CompensationTaskStatus.PENDING)
    }

    @Test
    fun `시나리오 D - cancel(CREATED, 미결제)시 Order CANCELLED, PG refund 호출 없음`() {
        val orderId = setupCreatedOrder(BigDecimal("5000.00"))
        val cancelKey = "cancel-d-${UUID.randomUUID()}"

        val result = orderUseCase.cancelOrder(CancelOrderCommand(orderId, cancelKey, reason = "변심"))

        assertThat(result.status).isEqualTo(OrderStatus.CANCELLED)
        val cancellation = cancellationRepository.findByIdempotencyKey(cancelKey)!!
        assertThat(cancellation.status).isEqualTo(CancellationStatus.SUCCEEDED)
        assertThat(cancellation.refundedAt).isNull()
        verify(exactly = 0) { paymentGateway.refund(any(), any()) }
    }

    @Test
    fun `cancel idempotency - 같은 키로 재요청시 replay (PG refund 재호출 없음)`() {
        val orderId = setupPaidOrder(BigDecimal("11000.00"))
        val payment = paymentRepository.findAll().first()
        val cancelKey = "cancel-replay-${UUID.randomUUID()}"

        every { paymentGateway.refund(payment.paymentKey!!, payment.amount) } returns
            RefundResult(refundedAt = LocalDateTime.now().withNano(0))

        orderUseCase.cancelOrder(CancelOrderCommand(orderId, cancelKey, reason = "1회차"))
        orderUseCase.cancelOrder(CancelOrderCommand(orderId, cancelKey, reason = "1회차"))

        verify(exactly = 1) { paymentGateway.refund(any(), any()) }
    }

    @Test
    fun `cancel idempotency - 같은 키 + 다른 order시 IdempotencyConflictException`() {
        val firstOrderId = setupCreatedOrder(BigDecimal("3000.00"))
        val secondOrderId = setupCreatedOrder(BigDecimal("4000.00"))
        val cancelKey = "cancel-conflict-${UUID.randomUUID()}"

        orderUseCase.cancelOrder(CancelOrderCommand(firstOrderId, cancelKey, reason = "first"))

        assertThatThrownBy {
            orderUseCase.cancelOrder(CancelOrderCommand(secondOrderId, cancelKey, reason = "first"))
        }.isInstanceOf(IdempotencyConflictException::class.java)
    }

    @Test
    fun `cancel idempotency - 같은 키 + 같은 order인데 reason이 다르면 IdempotencyConflictException`() {
        val orderId = setupCreatedOrder(BigDecimal("3500.00"))
        val cancelKey = "cancel-reason-conflict-${UUID.randomUUID()}"

        orderUseCase.cancelOrder(CancelOrderCommand(orderId, cancelKey, reason = "original"))

        assertThatThrownBy {
            orderUseCase.cancelOrder(CancelOrderCommand(orderId, cancelKey, reason = "different"))
        }.isInstanceOf(IdempotencyConflictException::class.java)
    }

    private fun setupCreatedOrder(amount: BigDecimal): Long {
        val customer = customerRepository.save(
            Customer(name = "통합-${UUID.randomUUID()}", email = "it-cancel@example.com")
        )
        val order = Order(
            customer = customer,
            orderNo = "ORD-IT-${UUID.randomUUID().toString().take(8).uppercase()}",
            shippingAddress = ShippingAddress("recip", "06236", "addr", ""),
            orderedAt = LocalDateTime.now()
        ).apply {
            replaceLines(listOf(OrderLineDraft("TestItem", 1, amount)))
        }
        return orderRepository.save(order).id!!
    }

    private fun setupPaidOrder(amount: BigDecimal): Long {
        val orderId = setupCreatedOrder(amount)
        val payKey = "pay-setup-${UUID.randomUUID()}"
        val approvedAt = LocalDateTime.now().withNano(0)
        every { paymentGateway.approve(amount, payKey) } returns
            ApproveResult(paymentKey = "MOCK-PG-${UUID.randomUUID()}", approvedAt = approvedAt)
        orderUseCase.payOrder(PayOrderCommand(orderId, payKey))
        return orderId
    }
}
