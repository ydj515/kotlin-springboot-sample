package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.compensation.CompensationOutcome
import com.example.kotlinspringbootsample.application.compensation.CompensationService
import com.example.kotlinspringbootsample.application.payment.PaymentLifecycleService
import com.example.kotlinspringbootsample.application.order.command.CancelOrderCommand
import com.example.kotlinspringbootsample.application.order.command.CreateOrderCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrdersCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrderStatusSummariesCommand
import com.example.kotlinspringbootsample.application.order.command.GetOrderCommand
import com.example.kotlinspringbootsample.application.order.command.OrderSearchMode
import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.application.order.command.ShipOrderCommand
import com.example.kotlinspringbootsample.application.order.result.OrderResult
import com.example.kotlinspringbootsample.application.order.result.OrderStatusSummaryResult
import com.example.kotlinspringbootsample.application.order.result.OrderSummaryResult
import com.example.kotlinspringbootsample.domain.customer.service.CustomerLookupService
import com.example.kotlinspringbootsample.domain.order.Cancellation
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.policy.OrderItemPolicy
import com.example.kotlinspringbootsample.domain.order.policy.OrderStatusTransitionPolicy
import com.example.kotlinspringbootsample.domain.order.event.OrderPaidEvent
import com.example.kotlinspringbootsample.domain.order.repository.CancellationRepository
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.order.service.OrderLookupService
import com.example.kotlinspringbootsample.domain.outbox.OutboxEvent
import com.example.kotlinspringbootsample.domain.outbox.repository.OutboxEventRepository
import com.example.kotlinspringbootsample.domain.payment.Payment
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.exception.IdempotencyConflictException
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentApprovalFailedException
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
@Transactional(readOnly = true)
class OrderUseCase(
    private val orderRepository: OrderRepository,
    private val customerLookupService: CustomerLookupService,
    private val orderLookupService: OrderLookupService,
    private val orderItemPolicy: OrderItemPolicy,
    private val orderStatusTransitionPolicy: OrderStatusTransitionPolicy,
    private val paymentGateway: PaymentGateway,
    private val paymentRepository: PaymentRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
    private val compensationService: CompensationService,
    private val cancellationRepository: CancellationRepository,
    private val paymentLifecycleService: PaymentLifecycleService
) {
    fun getOrders(command: FindOrdersCommand): Page<OrderSummaryResult> {
        val pageable = PageRequest.of(command.page, command.size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val customerName = command.customerName?.takeIf(String::isNotBlank)
        val orders = when (command.searchMode) {
            OrderSearchMode.DERIVED -> findOrdersByDerivedQuery(customerName, command.status, pageable)
            OrderSearchMode.JPQL -> orderRepository.searchByConditions(customerName, command.status, pageable)
        }

        return orders.map { it.toSummaryResult() }
    }

    fun getOrder(command: GetOrderCommand): OrderResult =
        orderLookupService.requireById(command.id).toResult()

    fun getOrderStatusSummaries(command: FindOrderStatusSummariesCommand): List<OrderStatusSummaryResult> =
        orderRepository.findStatusSummaries(
            customerName = command.customerName?.takeIf(String::isNotBlank),
            status = command.status
        )
            .map { it.toResult() }

    @Transactional
    fun createOrder(command: CreateOrderCommand): OrderResult {
        val customer = customerLookupService.requireById(command.customerId)

        val drafts: List<OrderLineDraft> = command.toDrafts()
        orderItemPolicy.validate(drafts)

        return Order(
            customer = customer,
            orderNo = generateOrderNo(),
            shippingAddress = command.toShippingAddress(),
            orderedAt = LocalDateTime.now(),
            deliveryRequestedAt = command.deliveryRequestedAt
        )
            .apply { replaceLines(drafts) }
            .let(orderRepository::save)
            .toResult()
    }

    @Transactional
    fun payOrder(command: PayOrderCommand): OrderResult {
        val order = orderLookupService.requireById(command.id)

        // 1. Idempotency check: 같은 키 이미 존재?
        val existing = paymentRepository.findByIdempotencyKey(command.idempotencyKey)
        if (existing != null) {
            return replayExistingPayment(existing, order, command)
        }

        // 2. 신규 결제: 상태 전이 검증 + Payment.REQUESTED 별도 commit (REQUIRES_NEW)
        orderStatusTransitionPolicy.validatePayable(order)
        val payment = paymentLifecycleService.createRequested(
            orderId = requireNotNull(order.id),
            idempotencyKey = command.idempotencyKey,
            amount = order.totalAmount
        )
        val paymentId = requireNotNull(payment.id)

        // 3. PG.approve — 시나리오 A (실패): Payment.FAILED 별도 commit + 예외 재전파, 보상 불필요
        val approveResult = try {
            paymentGateway.approve(order.totalAmount, command.idempotencyKey)
        } catch (e: PaymentApprovalFailedException) {
            paymentLifecycleService.markFailed(paymentId, reason = e.message ?: "PG declined")
            throw e
        }

        // 4. Payment.APPROVED 별도 commit (REQUIRES_NEW) — 메인 롤백과 무관하게 audit 보존
        paymentLifecycleService.markApproved(paymentId, approveResult.paymentKey, approveResult.approvedAt)

        // 5. 시나리오 B 진입: 여기 이후 실패는 모두 보상(자동 환불) 대상.
        return try {
            order.markPaid(approveResult.approvedAt)
            publishOrderPaidEventToOutbox(order, payment, approveResult.paymentKey, approveResult.approvedAt)
            order.toResult().copy(paymentKey = approveResult.paymentKey)
        } catch (e: Exception) {
            // 메인 트랜잭션은 롤백 예정. 보상은 별도 트랜잭션(REQUIRES_NEW).
            compensationService.compensateApprovedPayment(
                paymentId = paymentId,
                paymentKey = approveResult.paymentKey,
                amount = payment.amount,
                reason = "payOrder downstream failure: ${e.message}"
            )
            throw e
        }
    }

    private fun publishOrderPaidEventToOutbox(
        order: Order,
        payment: Payment,
        paymentKey: String,
        paidAt: java.time.LocalDateTime
    ) {
        val event = OrderPaidEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = requireNotNull(order.id),
            paymentId = requireNotNull(payment.id),
            paymentKey = paymentKey,
            amount = payment.amount,
            paidAt = paidAt
        )
        outboxEventRepository.save(
            OutboxEvent.pending(
                aggregateType = OrderPaidEvent.AGGREGATE_TYPE,
                aggregateId = event.orderId.toString(),
                eventType = OrderPaidEvent.EVENT_TYPE,
                payload = objectMapper.writeValueAsString(event)
            )
        )
    }

    private fun replayExistingPayment(
        existing: Payment,
        order: Order,
        command: PayOrderCommand
    ): OrderResult {
        if (existing.orderId != order.id) {
            throw IdempotencyConflictException(
                "idempotency key already used for another order (orderId=${existing.orderId})"
            )
        }
        if (existing.amount.compareTo(order.totalAmount) != 0) {
            throw IdempotencyConflictException(
                "idempotency key was used with a different amount (expected=${existing.amount}, requested=${order.totalAmount})"
            )
        }
        return when (existing.status) {
            PaymentStatus.APPROVED -> order.toResult().copy(paymentKey = existing.paymentKey)
            PaymentStatus.FAILED -> throw PaymentApprovalFailedException(
                "payment previously failed for this idempotency key"
            )
            PaymentStatus.REQUESTED -> throw IdempotencyConflictException(
                "payment for this idempotency key is still in progress"
            )
            else -> throw IdempotencyConflictException(
                "payment for this idempotency key is in an unrecoverable state: ${existing.status}"
            )
        }
    }

    @Transactional
    fun shipOrder(command: ShipOrderCommand): OrderResult =
        orderLookupService.requireById(command.id)
            .apply {
                orderStatusTransitionPolicy.validateShippable(this)
                markShipped()
            }
            .toResult()

    @Transactional
    fun cancelOrder(command: CancelOrderCommand): OrderResult {
        val order = orderLookupService.requireById(command.id)

        // 1. Idempotency check
        val existing = cancellationRepository.findByIdempotencyKey(command.idempotencyKey)
        if (existing != null) {
            return replayExistingCancellation(existing, order, command)
        }

        // 2. 신규 cancellation 전이 검증
        orderStatusTransitionPolicy.validateCancellable(order)
        val cancellation = cancellationRepository.save(
            Cancellation.requested(
                orderId = requireNotNull(order.id),
                idempotencyKey = command.idempotencyKey,
                reason = command.reason
            )
        )

        val wasPaid = order.status == OrderStatus.PAID
        order.cancel(reason = command.reason)

        if (wasPaid) {
            val approvedPayment = paymentRepository.findByOrderId(requireNotNull(order.id))
                .firstOrNull { it.status == PaymentStatus.APPROVED }
                ?: throw IllegalStateException("PAID order has no APPROVED payment: orderId=${order.id}")

            val outcome = compensationService.compensateApprovedPayment(
                paymentId = requireNotNull(approvedPayment.id),
                paymentKey = requireNotNull(approvedPayment.paymentKey),
                amount = approvedPayment.amount,
                reason = "order cancel: ${command.reason ?: "user requested"}"
            )
            when (outcome) {
                is CompensationOutcome.Refunded -> cancellation.markSucceeded(outcome.refundedAt)
                is CompensationOutcome.Scheduled -> cancellation.markRefundFailed()
            }
        } else {
            // CREATED 상태였음 — 환불 호출 불필요, 즉시 SUCCEEDED
            cancellation.markSucceeded(refundedAt = null)
        }

        cancellationRepository.save(cancellation)
        return order.toResult()
    }

    private fun replayExistingCancellation(
        existing: Cancellation,
        order: Order,
        command: CancelOrderCommand
    ): OrderResult {
        if (existing.orderId != order.id) {
            throw IdempotencyConflictException(
                "idempotency key already used for another order (orderId=${existing.orderId})"
            )
        }
        if ((existing.reason ?: "") != (command.reason ?: "")) {
            throw IdempotencyConflictException(
                "idempotency key was used with a different reason (expected=${existing.reason}, requested=${command.reason})"
            )
        }
        return order.toResult()
    }

    private fun findOrdersByDerivedQuery(
        customerName: String?,
        status: OrderStatus?,
        pageable: PageRequest
    ): Page<Order> =
        when {
            customerName != null && status != null ->
                orderRepository.findAllByCustomerNameAndStatusAndDeletedAtIsNull(customerName, status, pageable)

            customerName != null ->
                orderRepository.findAllByCustomerNameAndDeletedAtIsNull(customerName, pageable)

            status != null ->
                orderRepository.findAllByDeletedAtIsNullAndStatus(status, pageable)

            else ->
                orderRepository.findAllByDeletedAtIsNull(pageable)
        }

    private fun generateOrderNo(): String {
        val datePart = LocalDate.now().format(DATE_FORMATTER)
        val randomPart = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        return "ORD-$datePart-$randomPart"
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
