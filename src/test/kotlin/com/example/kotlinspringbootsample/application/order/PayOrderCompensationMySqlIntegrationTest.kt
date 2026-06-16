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
import com.example.kotlinspringbootsample.domain.payment.PaymentCompletionTaskStatus
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentApprovalFailedException
import com.example.kotlinspringbootsample.domain.payment.gateway.ApproveResult
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import com.example.kotlinspringbootsample.domain.payment.gateway.RefundResult
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentCompletionTaskRepository
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
import com.example.kotlinspringbootsample.infrastructure.payment.PaymentCompletionRetryWorker
import com.example.kotlinspringbootsample.support.MySqlIntegrationTestSupport
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@TestPropertySource(properties = ["app.payment-completion.worker.initial-delay-ms=600000"])
class PayOrderCompensationMySqlIntegrationTest @Autowired constructor(
    private val orderUseCase: OrderUseCase,
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentCompletionTaskRepository: PaymentCompletionTaskRepository,
    private val compensationTaskRepository: CompensationTaskRepository,
    private val paymentCompletionRetryWorker: PaymentCompletionRetryWorker,
    @MockkBean private val paymentGateway: PaymentGateway,
    @MockkBean(relaxed = true) private val outboxEventRepository: OutboxEventRepository
) : MySqlIntegrationTestSupport() {

    private val createdOrderIds = mutableListOf<Long>()
    private val createdCustomerIds = mutableListOf<Long>()
    private val createdPaymentKeys = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        paymentCompletionTaskRepository.deleteAll()
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
        val approveTxActive = AtomicBoolean(true)

        every { paymentGateway.approve(any(), key) } answers {
            approveTxActive.set(TransactionSynchronizationManager.isActualTransactionActive())
            throw PaymentApprovalFailedException("PG declined: insufficient funds")
        }

        assertThatThrownBy { orderUseCase.payOrder(PayOrderCommand(orderId, key)) }
            .isInstanceOf(PaymentApprovalFailedException::class.java)
            .hasMessageContaining("insufficient funds")

        val order = orderRepository.findById(orderId).orElseThrow()
        assertThat(order.status).isEqualTo(OrderStatus.CREATED)

        val payment = paymentRepository.findByIdempotencyKey(key)
        assertThat(payment).isNotNull
        assertThat(payment!!.status).isEqualTo(PaymentStatus.FAILED)

        assertThat(approveTxActive.get()).isFalse
        verify(exactly = 0) { paymentGateway.refund(any(), any()) }
        assertThat(compensationTaskRepository.count()).isZero
    }

    @Test
    fun `시나리오 B-1 - approve 성공 후 outbox 저장 실패시 pending 저장 후 worker 재시도로 PAID 복구된다`() {
        val orderId = setupCreatedOrder(amount = BigDecimal("20000.00"))
        val key = "scenario-b1-${UUID.randomUUID()}"
        createdPaymentKeys += key
        val approvedAt = LocalDateTime.now().withNano(0)
        val approveTxActive = AtomicBoolean(true)
        val refundTxActive = AtomicBoolean(false)

        every { paymentGateway.approve(any(), key) } answers {
            approveTxActive.set(TransactionSynchronizationManager.isActualTransactionActive())
            ApproveResult(paymentKey = "MOCK-PG-b1", approvedAt = approvedAt)
        }
        every { paymentGateway.refund("MOCK-PG-b1", any()) } answers {
            refundTxActive.set(TransactionSynchronizationManager.isActualTransactionActive())
            RefundResult(refundedAt = approvedAt.plusSeconds(1))
        }
        every { outboxEventRepository.save(any()) } throws
            RuntimeException("simulated outbox failure")

        val result = orderUseCase.payOrder(PayOrderCommand(orderId, key))
        assertThat(result.status.name).isEqualTo("PROCESSING")

        val order = orderRepository.findById(orderId).orElseThrow()
        assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_COMPLETION_PENDING)
        assertThat(order.paymentCompletionPendingAt).isNotNull

        val payment = paymentRepository.findByIdempotencyKey(key)
        assertThat(payment).isNotNull
        assertThat(payment!!.status).isEqualTo(PaymentStatus.APPROVED)

        assertThat(approveTxActive.get()).isFalse
        assertThat(refundTxActive.get()).isFalse
        verify(exactly = 0) { paymentGateway.refund("MOCK-PG-b1", any()) }
        assertThat(compensationTaskRepository.count()).isZero

        val pendingTasks = paymentCompletionTaskRepository.findAll()
        assertThat(pendingTasks).hasSize(1)
        assertThat(pendingTasks.first().status).isEqualTo(PaymentCompletionTaskStatus.PENDING)

        every { outboxEventRepository.save(any()) } answers { firstArg() }

        paymentCompletionRetryWorker.runBatch()

        val recoveredOrder = orderRepository.findById(orderId).orElseThrow()
        assertThat(recoveredOrder.status).isEqualTo(OrderStatus.PAID)
        assertThat(recoveredOrder.paidAt).isEqualTo(approvedAt)

        val recoveredTask = paymentCompletionTaskRepository.findAll().first()
        assertThat(recoveredTask.status).isEqualTo(PaymentCompletionTaskStatus.SUCCESS)
    }

    @Test
    fun `시나리오 B-2 - completion retry 한도 초과 후 PG refund 실패시 CompensationTask PENDING 1건 생성`() {
        val orderId = setupCreatedOrder(amount = BigDecimal("30000.00"))
        val key = "scenario-b2-${UUID.randomUUID()}"
        createdPaymentKeys += key
        val approvedAt = LocalDateTime.now().withNano(0)
        val approveTxActive = AtomicBoolean(true)
        val refundTxActive = AtomicBoolean(true)

        every { paymentGateway.approve(any(), key) } answers {
            approveTxActive.set(TransactionSynchronizationManager.isActualTransactionActive())
            ApproveResult(paymentKey = "MOCK-PG-b2", approvedAt = approvedAt)
        }
        every { paymentGateway.refund("MOCK-PG-b2", any()) } answers {
            refundTxActive.set(TransactionSynchronizationManager.isActualTransactionActive())
            throw RuntimeException("simulated PG refund failure")
        }
        every { outboxEventRepository.save(any()) } throws
            RuntimeException("simulated outbox failure")

        val result = orderUseCase.payOrder(PayOrderCommand(orderId, key))
        assertThat(result.status.name).isEqualTo("PROCESSING")

        val order = orderRepository.findById(orderId).orElseThrow()
        assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_COMPLETION_PENDING)

        val payment = paymentRepository.findByIdempotencyKey(key)
        assertThat(payment).isNotNull
        assertThat(payment!!.status).isEqualTo(PaymentStatus.APPROVED)

        val completionTask = paymentCompletionTaskRepository.findAll().single()
        completionTask.retryCount = 5
        completionTask.nextAttemptAt = LocalDateTime.now().minusSeconds(1)
        paymentCompletionTaskRepository.save(completionTask)

        paymentCompletionRetryWorker.runBatch()

        assertThat(approveTxActive.get()).isFalse
        assertThat(refundTxActive.get()).isFalse
        verify(exactly = 1) { paymentGateway.refund("MOCK-PG-b2", any()) }

        val failedCompletionTask = paymentCompletionTaskRepository.findAll().single()
        assertThat(failedCompletionTask.status).isEqualTo(PaymentCompletionTaskStatus.FAILED)

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
