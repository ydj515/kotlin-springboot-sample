package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.domain.compensation.CompensationTaskStatus
import com.example.kotlinspringbootsample.domain.compensation.CompensationTaskType
import com.example.kotlinspringbootsample.domain.compensation.repository.CompensationTaskRepository
import com.example.kotlinspringbootsample.domain.customer.Customer
import com.example.kotlinspringbootsample.domain.customer.repository.CustomerRepository
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.ShippingAddress
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.outbox.repository.OutboxEventRepository
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentApprovalFailedException
import com.example.kotlinspringbootsample.domain.payment.gateway.ApproveResult
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
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

class PayOrderCompensationMySqlIntegrationTest @Autowired constructor(
    private val orderUseCase: OrderUseCase,
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val compensationTaskRepository: CompensationTaskRepository,
    @MockkBean private val paymentGateway: PaymentGateway,
    @MockkBean(relaxed = true) private val outboxEventRepository: OutboxEventRepository
) : MySqlIntegrationTestSupport() {

    private val createdOrderIds = mutableListOf<Long>()
    private val createdCustomerIds = mutableListOf<Long>()
    private val createdPaymentKeys = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        compensationTaskRepository.deleteAll()
        createdPaymentKeys.forEach { key ->
            paymentRepository.findByIdempotencyKey(key)?.let { paymentRepository.delete(it) }
        }
        createdPaymentKeys.clear()
        createdOrderIds.forEach { id ->
            orderRepository.findById(id).ifPresent { orderRepository.delete(it) }
        }
        createdOrderIds.clear()
        createdCustomerIds.forEach { id ->
            customerRepository.findById(id).ifPresent { customerRepository.delete(it) }
        }
        createdCustomerIds.clear()
    }

    @Test
    fun `시나리오 A - PG approve 실패시 Payment FAILED와 Order CREATED 유지, refund 호출 없음`() {
        val orderId = setupCreatedOrder(amount = BigDecimal("10000.00"))
        val key = "scenario-a-${UUID.randomUUID()}"
        createdPaymentKeys += key

        every { paymentGateway.approve(any(), key) } throws
            PaymentApprovalFailedException("PG declined: insufficient funds")

        assertThatThrownBy { orderUseCase.payOrder(PayOrderCommand(orderId, key)) }
            .isInstanceOf(PaymentApprovalFailedException::class.java)
            .hasMessageContaining("insufficient funds")

        val order = orderRepository.findById(orderId).orElseThrow()
        assertThat(order.status).isEqualTo(OrderStatus.CREATED)

        val payment = paymentRepository.findByIdempotencyKey(key)
        assertThat(payment).isNotNull
        assertThat(payment!!.status).isEqualTo(PaymentStatus.FAILED)

        verify(exactly = 0) { paymentGateway.refund(any(), any()) }
        assertThat(compensationTaskRepository.count()).isZero
    }

    @Test
    fun `시나리오 B-1 - approve 성공 후 outbox 실패 + PG refund 성공시 Order 롤백, refund 호출됨, CompensationTask 없음`() {
        val orderId = setupCreatedOrder(amount = BigDecimal("20000.00"))
        val key = "scenario-b1-${UUID.randomUUID()}"
        createdPaymentKeys += key
        val approvedAt = LocalDateTime.now().withNano(0)

        every { paymentGateway.approve(any(), key) } returns
            ApproveResult(paymentKey = "MOCK-PG-b1", approvedAt = approvedAt)
        every { paymentGateway.refund("MOCK-PG-b1", any()) } returns
            com.example.kotlinspringbootsample.domain.payment.gateway.RefundResult(refundedAt = approvedAt.plusSeconds(1))
        every { outboxEventRepository.save(any()) } throws
            RuntimeException("simulated outbox failure")

        assertThatThrownBy { orderUseCase.payOrder(PayOrderCommand(orderId, key)) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("simulated outbox failure")

        // 메인 트랜잭션은 롤백 — Order는 CREATED 유지
        val order = orderRepository.findById(orderId).orElseThrow()
        assertThat(order.status).isEqualTo(OrderStatus.CREATED)

        // PaymentLifecycleService(REQUIRES_NEW)로 audit이 보존되어 Payment.REFUNDED로 종결
        val payment = paymentRepository.findByIdempotencyKey(key)
        assertThat(payment).isNotNull
        assertThat(payment!!.status).isEqualTo(PaymentStatus.REFUNDED)

        // 별도 REQUIRES_NEW 트랜잭션에서 PG.refund 호출 + CompensationTask 없음 (즉시 성공)
        verify(exactly = 1) { paymentGateway.refund("MOCK-PG-b1", any()) }
        assertThat(compensationTaskRepository.count()).isZero
    }

    @Test
    fun `시나리오 B-2 - approve 성공 후 outbox 실패 + PG refund 실패시 CompensationTask PENDING 1건 생성`() {
        val orderId = setupCreatedOrder(amount = BigDecimal("30000.00"))
        val key = "scenario-b2-${UUID.randomUUID()}"
        createdPaymentKeys += key
        val approvedAt = LocalDateTime.now().withNano(0)

        every { paymentGateway.approve(any(), key) } returns
            ApproveResult(paymentKey = "MOCK-PG-b2", approvedAt = approvedAt)
        every { paymentGateway.refund("MOCK-PG-b2", any()) } throws
            RuntimeException("simulated PG refund failure")
        every { outboxEventRepository.save(any()) } throws
            RuntimeException("simulated outbox failure")

        assertThatThrownBy { orderUseCase.payOrder(PayOrderCommand(orderId, key)) }
            .isInstanceOf(RuntimeException::class.java)

        val order = orderRepository.findById(orderId).orElseThrow()
        assertThat(order.status).isEqualTo(OrderStatus.CREATED)

        // Payment.REFUND_FAILED 별도 commit으로 audit 보존
        val payment = paymentRepository.findByIdempotencyKey(key)
        assertThat(payment).isNotNull
        assertThat(payment!!.status).isEqualTo(PaymentStatus.REFUND_FAILED)

        verify(exactly = 1) { paymentGateway.refund("MOCK-PG-b2", any()) }

        val tasks = compensationTaskRepository.findAll()
        assertThat(tasks).hasSize(1)
        val task = tasks.first()
        assertThat(task.taskType).isEqualTo(CompensationTaskType.PG_REFUND)
        assertThat(task.status).isEqualTo(CompensationTaskStatus.PENDING)
        assertThat(task.retryCount).isZero
        assertThat(task.payload).contains("MOCK-PG-b2")
    }

    private fun setupCreatedOrder(amount: BigDecimal): Long {
        val customer = customerRepository.save(
            Customer(name = "통합-${UUID.randomUUID()}", email = "it@example.com")
        )
        createdCustomerIds += customer.id!!

        val order = Order(
            customer = customer,
            orderNo = "ORD-IT-${UUID.randomUUID().toString().take(8).uppercase()}",
            shippingAddress = ShippingAddress(
                recipient = customer.name,
                zipCode = "06236",
                address1 = "Test Address",
                address2 = ""
            ),
            orderedAt = LocalDateTime.now()
        ).apply {
            replaceLines(
                listOf(
                    OrderLineDraft(productName = "TestItem", quantity = 1, unitPrice = amount)
                )
            )
        }
        val saved = orderRepository.save(order)
        createdOrderIds += saved.id!!
        return saved.id!!
    }
}
