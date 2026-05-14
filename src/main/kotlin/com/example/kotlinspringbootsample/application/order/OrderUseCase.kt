package com.example.kotlinspringbootsample.application.order

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
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.policy.OrderItemPolicy
import com.example.kotlinspringbootsample.domain.order.policy.OrderStatusTransitionPolicy
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.order.service.OrderLookupService
import com.example.kotlinspringbootsample.domain.payment.Payment
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.exception.IdempotencyConflictException
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentApprovalFailedException
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
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
    private val paymentRepository: PaymentRepository
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

        // 2. 신규 결제: 상태 전이 검증 + Payment.REQUESTED 저장
        orderStatusTransitionPolicy.validatePayable(order)
        val payment = paymentRepository.save(
            Payment.request(
                orderId = requireNotNull(order.id),
                idempotencyKey = command.idempotencyKey,
                amount = order.totalAmount
            )
        )

        // 3. PG.approve 호출 (실패 시 Payment.FAILED로 기록 후 예외 재전파)
        val approveResult = try {
            paymentGateway.approve(order.totalAmount, command.idempotencyKey)
        } catch (e: PaymentApprovalFailedException) {
            payment.markFailed(reason = e.message ?: "PG declined")
            paymentRepository.save(payment)
            throw e
        }

        // 4. 성공: Payment.APPROVED + Order.markPaid
        payment.markApproved(approveResult.paymentKey, approveResult.approvedAt)
        paymentRepository.save(payment)
        order.markPaid(approveResult.approvedAt)

        return order.toResult().copy(paymentKey = approveResult.paymentKey)
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
    fun cancelOrder(command: CancelOrderCommand): OrderResult =
        orderLookupService.requireById(command.id)
            .apply {
                orderStatusTransitionPolicy.validateCancellable(this)
                cancel()
            }
            .toResult()

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
