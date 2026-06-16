package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.order.command.CancelOrderCommand
import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.application.order.result.OrderResult
import com.example.kotlinspringbootsample.application.order.result.PayOrderResult
import com.example.kotlinspringbootsample.application.compensation.CompensationOutcome
import com.example.kotlinspringbootsample.domain.order.Cancellation
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.exception.InvalidOrderStatusTransitionException
import com.example.kotlinspringbootsample.domain.order.exception.OrderNotFoundException
import com.example.kotlinspringbootsample.domain.order.event.OrderPaidEvent
import com.example.kotlinspringbootsample.domain.order.policy.OrderStatusTransitionPolicy
import com.example.kotlinspringbootsample.domain.order.repository.CancellationRepository
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.outbox.OutboxEvent
import com.example.kotlinspringbootsample.domain.outbox.repository.OutboxEventRepository
import com.example.kotlinspringbootsample.domain.payment.Payment
import com.example.kotlinspringbootsample.domain.payment.PaymentCompletionTask
import com.example.kotlinspringbootsample.domain.payment.PaymentCompletionTaskStatus
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.exception.IdempotencyConflictException
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentApprovalFailedException
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentNotFoundException
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentCompletionTaskRepository
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Service
class OrderPaymentTransactionService(
    private val orderRepository: OrderRepository,
    private val orderStatusTransitionPolicy: OrderStatusTransitionPolicy,
    private val paymentRepository: PaymentRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
    private val cancellationRepository: CancellationRepository,
    private val paymentCompletionTaskRepository: PaymentCompletionTaskRepository
) {

    @Transactional
    fun preparePayOrder(command: PayOrderCommand): PayOrderPreparation {
        val order = requireOrderForUpdate(command.id)

        val existing = paymentRepository.findByIdempotencyKey(command.idempotencyKey)
        if (existing != null) {
            return PayOrderPreparation.Replay(replayExistingPayment(existing, order.toResult(), command))
        }

        orderStatusTransitionPolicy.validatePayable(order)
        rejectIfOrderHasActivePayment(requireNotNull(order.id))
        val payment = paymentRepository.save(
            Payment.request(
                orderId = requireNotNull(order.id),
                idempotencyKey = command.idempotencyKey,
                amount = order.totalAmount
            )
        )

        return PayOrderPreparation.ApprovalRequired(
            orderId = requireNotNull(order.id),
            paymentId = requireNotNull(payment.id),
            amount = payment.amount,
            idempotencyKey = command.idempotencyKey
        )
    }

    @Transactional
    fun markPaymentApproved(paymentId: Long, paymentKey: String, approvedAt: LocalDateTime) {
        val payment = findPayment(paymentId)
        payment.markApproved(paymentKey = paymentKey, approvedAt = approvedAt)
        paymentRepository.save(payment)
    }

    @Transactional
    fun markPaymentFailed(paymentId: Long, reason: String, occurredAt: LocalDateTime = LocalDateTime.now()) {
        val payment = findPayment(paymentId)
        payment.markFailed(reason = reason, occurredAt = occurredAt)
        paymentRepository.save(payment)
    }

    @Transactional
    fun completePayOrder(
        orderId: Long,
        paymentId: Long,
        paymentKey: String,
        approvedAt: LocalDateTime
    ): OrderResult {
        val order = requireOrderForUpdate(orderId)
        val payment = findPayment(paymentId)

        if (payment.orderId != order.id) {
            throw NonRecoverablePaymentCompletionException(
                "payment order mismatch: paymentId=$paymentId paymentOrderId=${payment.orderId} orderId=${order.id}"
            )
        }
        if (payment.status != PaymentStatus.APPROVED || payment.paymentKey != paymentKey) {
            throw NonRecoverablePaymentCompletionException(
                "payment must be APPROVED before completing order: paymentId=$paymentId status=${payment.status}"
            )
        }

        if (order.status == OrderStatus.PAID || order.status == OrderStatus.SHIPPED) {
            return order.toResult().copy(paymentKey = paymentKey)
        }

        try {
            orderStatusTransitionPolicy.validatePaymentCompletable(order)
        } catch (e: InvalidOrderStatusTransitionException) {
            throw NonRecoverablePaymentCompletionException(e.message ?: "order cannot be completed", e)
        }
        order.markPaid(approvedAt)
        publishOrderPaidEventToOutbox(orderId, payment, paymentKey, approvedAt)

        return order.toResult().copy(paymentKey = paymentKey)
    }

    @Transactional
    fun markPaymentCompletionPendingAndSchedule(
        orderId: Long,
        paymentId: Long,
        paymentKey: String,
        amount: BigDecimal,
        approvedAt: LocalDateTime,
        reason: String,
        now: LocalDateTime = LocalDateTime.now()
    ): OrderResult {
        val order = requireOrderForUpdate(orderId)
        val payment = findPayment(paymentId)

        if (payment.orderId != order.id || payment.status != PaymentStatus.APPROVED || payment.paymentKey != paymentKey) {
            throw NonRecoverablePaymentCompletionException(
                "payment cannot be scheduled for completion retry: paymentId=$paymentId status=${payment.status}"
            )
        }
        if (order.status == OrderStatus.PAID || order.status == OrderStatus.SHIPPED) {
            return order.toResult().copy(paymentKey = paymentKey)
        }

        try {
            orderStatusTransitionPolicy.validatePaymentCompletable(order)
        } catch (e: InvalidOrderStatusTransitionException) {
            throw NonRecoverablePaymentCompletionException(e.message ?: "order cannot be scheduled for completion", e)
        }

        order.markPaymentCompletionPending(reason = reason, pendingAt = now)
        if (paymentCompletionTaskRepository.findFirstByPaymentIdAndStatus(
                paymentId,
                PaymentCompletionTaskStatus.PENDING
            ) == null
        ) {
            paymentCompletionTaskRepository.save(
                PaymentCompletionTask.pending(
                    orderId = orderId,
                    paymentId = paymentId,
                    paymentKey = paymentKey,
                    amount = amount,
                    approvedAt = approvedAt,
                    reason = reason,
                    now = now
                )
            )
        }

        return order.toResult().copy(paymentKey = paymentKey)
    }

    @Transactional(readOnly = true)
    fun getOrderResult(orderId: Long, paymentKey: String? = null): OrderResult =
        orderRepository.findByIdAndDeletedAtIsNull(orderId)
            ?.toResult()
            ?.copy(paymentKey = paymentKey)
            ?: throw OrderNotFoundException("Order not found with id $orderId")

    @Transactional
    fun prepareCancelOrder(command: CancelOrderCommand): CancelOrderPreparation {
        val order = requireOrderForUpdate(command.id)

        val existing = cancellationRepository.findByIdempotencyKey(command.idempotencyKey)
        if (existing != null) {
            return CancelOrderPreparation.Replay(replayExistingCancellation(existing, order.toResult(), command))
        }

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

        if (!wasPaid) {
            cancellation.markSucceeded(refundedAt = null)
            return CancelOrderPreparation.Completed(order.toResult())
        }

        val approvedPayment = paymentRepository.findByOrderId(requireNotNull(order.id))
            .firstOrNull { it.status == PaymentStatus.APPROVED }
            ?: throw IllegalStateException("PAID order has no APPROVED payment: orderId=${order.id}")

        return CancelOrderPreparation.RefundRequired(
            result = order.toResult(),
            cancellationId = requireNotNull(cancellation.id),
            paymentId = requireNotNull(approvedPayment.id),
            paymentKey = requireNotNull(approvedPayment.paymentKey),
            amount = approvedPayment.amount,
            reason = "order cancel: ${command.reason ?: "user requested"}"
        )
    }

    @Transactional
    fun recordCancellationRefundOutcome(cancellationId: Long, outcome: CompensationOutcome) {
        val cancellation = cancellationRepository.findById(cancellationId).orElseThrow {
            IllegalStateException("cancellation not found: id=$cancellationId")
        }

        when (outcome) {
            is CompensationOutcome.Refunded -> cancellation.markSucceeded(outcome.refundedAt)
            is CompensationOutcome.Scheduled -> cancellation.markRefundFailed()
        }
        cancellationRepository.save(cancellation)
    }

    private fun publishOrderPaidEventToOutbox(
        orderId: Long,
        payment: Payment,
        paymentKey: String,
        paidAt: LocalDateTime
    ) {
        val event = OrderPaidEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = orderId,
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

    private fun findPayment(paymentId: Long): Payment =
        paymentRepository.findById(paymentId).orElseThrow {
            PaymentNotFoundException("payment not found: id=$paymentId")
        }

    private fun requireOrderForUpdate(orderId: Long) =
        orderRepository.findByIdAndDeletedAtIsNullForUpdate(orderId)
            ?: throw OrderNotFoundException("Order not found with id $orderId")

    private fun rejectIfOrderHasActivePayment(orderId: Long) {
        val activePayment = paymentRepository.findByOrderId(orderId)
            .firstOrNull {
                it.status == PaymentStatus.REQUESTED ||
                    it.status == PaymentStatus.APPROVED ||
                    it.status == PaymentStatus.REFUND_FAILED
            }
            ?: return

        throw IdempotencyConflictException(
            "order already has an active payment (paymentId=${activePayment.id}, status=${activePayment.status})"
        )
    }

    private fun replayExistingPayment(
        existing: Payment,
        orderResult: OrderResult,
        command: PayOrderCommand
    ): PayOrderResult {
        if (existing.orderId != command.id) {
            throw IdempotencyConflictException(
                "idempotency key already used for another order (orderId=${existing.orderId})"
            )
        }
        if (existing.amount.compareTo(orderResult.totalAmount) != 0) {
            throw IdempotencyConflictException(
                "idempotency key was used with a different amount (expected=${existing.amount}, requested=${orderResult.totalAmount})"
            )
        }
        return when (existing.status) {
            PaymentStatus.APPROVED -> replayApprovedPayment(existing, orderResult)
            PaymentStatus.REFUNDED -> PayOrderResult.canceled(orderResult.copy(paymentKey = existing.paymentKey))
            PaymentStatus.REFUND_FAILED -> PayOrderResult.canceling(orderResult.copy(paymentKey = existing.paymentKey))
            PaymentStatus.FAILED -> throw PaymentApprovalFailedException(
                "payment previously failed for this idempotency key"
            )
            PaymentStatus.REQUESTED -> throw IdempotencyConflictException(
                "payment for this idempotency key is still in progress"
            )
        }
    }

    private fun replayApprovedPayment(existing: Payment, orderResult: OrderResult): PayOrderResult {
        val paymentKey = requireNotNull(existing.paymentKey) {
            "approved payment has no payment key: paymentId=${existing.id}"
        }
        val approvedAt = requireNotNull(existing.approvedAt) {
            "approved payment has no approvedAt: paymentId=${existing.id}"
        }
        return when (orderResult.status) {
            OrderStatus.PAID,
            OrderStatus.SHIPPED -> PayOrderResult.paid(orderResult.copy(paymentKey = paymentKey))

            OrderStatus.CREATED,
            OrderStatus.PAYMENT_COMPLETION_PENDING -> {
                val pending = markPaymentCompletionPendingAndSchedule(
                    orderId = orderResult.id,
                    paymentId = requireNotNull(existing.id),
                    paymentKey = paymentKey,
                    amount = existing.amount,
                    approvedAt = approvedAt,
                    reason = "replay detected approved payment without completed order"
                )
                PayOrderResult.processing(pending)
            }

            OrderStatus.CANCELLED -> PayOrderResult.canceling(orderResult.copy(paymentKey = paymentKey))
        }
    }

    private fun replayExistingCancellation(
        existing: Cancellation,
        orderResult: OrderResult,
        command: CancelOrderCommand
    ): OrderResult {
        if (existing.orderId != command.id) {
            throw IdempotencyConflictException(
                "idempotency key already used for another order (orderId=${existing.orderId})"
            )
        }
        if ((existing.reason ?: "") != (command.reason ?: "")) {
            throw IdempotencyConflictException(
                "idempotency key was used with a different reason (expected=${existing.reason}, requested=${command.reason})"
            )
        }
        return orderResult
    }
}

sealed interface PayOrderPreparation {
    data class Replay(val result: PayOrderResult) : PayOrderPreparation

    data class ApprovalRequired(
        val orderId: Long,
        val paymentId: Long,
        val amount: BigDecimal,
        val idempotencyKey: String
    ) : PayOrderPreparation
}

sealed interface CancelOrderPreparation {
    data class Replay(val result: OrderResult) : CancelOrderPreparation

    data class Completed(val result: OrderResult) : CancelOrderPreparation

    data class RefundRequired(
        val result: OrderResult,
        val cancellationId: Long,
        val paymentId: Long,
        val paymentKey: String,
        val amount: BigDecimal,
        val reason: String
    ) : CancelOrderPreparation
}
