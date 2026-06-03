package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.order.command.CancelOrderCommand
import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.application.order.result.OrderResult
import com.example.kotlinspringbootsample.application.compensation.CompensationOutcome
import com.example.kotlinspringbootsample.domain.order.Cancellation
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.exception.OrderNotFoundException
import com.example.kotlinspringbootsample.domain.order.event.OrderPaidEvent
import com.example.kotlinspringbootsample.domain.order.policy.OrderStatusTransitionPolicy
import com.example.kotlinspringbootsample.domain.order.repository.CancellationRepository
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.outbox.OutboxEvent
import com.example.kotlinspringbootsample.domain.outbox.repository.OutboxEventRepository
import com.example.kotlinspringbootsample.domain.payment.Payment
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.exception.IdempotencyConflictException
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentApprovalFailedException
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentNotFoundException
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
    private val cancellationRepository: CancellationRepository
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

        require(payment.orderId == order.id) {
            "payment order mismatch: paymentId=$paymentId paymentOrderId=${payment.orderId} orderId=${order.id}"
        }
        require(payment.status == PaymentStatus.APPROVED && payment.paymentKey == paymentKey) {
            "payment must be APPROVED before completing order: paymentId=$paymentId status=${payment.status}"
        }

        orderStatusTransitionPolicy.validatePayable(order)
        order.markPaid(approvedAt)
        publishOrderPaidEventToOutbox(orderId, payment, paymentKey, approvedAt)

        return order.toResult().copy(paymentKey = paymentKey)
    }

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
            .firstOrNull { it.status == PaymentStatus.REQUESTED || it.status == PaymentStatus.APPROVED }
            ?: return

        throw IdempotencyConflictException(
            "order already has an active payment (paymentId=${activePayment.id}, status=${activePayment.status})"
        )
    }

    private fun replayExistingPayment(
        existing: Payment,
        orderResult: OrderResult,
        command: PayOrderCommand
    ): OrderResult {
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
            PaymentStatus.APPROVED -> orderResult.copy(paymentKey = existing.paymentKey)
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
    data class Replay(val result: OrderResult) : PayOrderPreparation

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
