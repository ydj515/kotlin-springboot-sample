package com.example.kotlinspringbootsample.domain.payment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment_completion_tasks")
class PaymentCompletionTask(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "order_id", nullable = false)
    var orderId: Long,

    @Column(name = "payment_id", nullable = false)
    var paymentId: Long,

    @Column(name = "payment_key", nullable = false, length = 100)
    var paymentKey: String,

    @Column(nullable = false, precision = 12, scale = 2)
    var amount: BigDecimal,

    @Column(name = "approved_at", nullable = false)
    var approvedAt: LocalDateTime,

    @Column(nullable = false, length = 500)
    var reason: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PaymentCompletionTaskStatus = PaymentCompletionTaskStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "last_error", length = 1000)
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun markSuccess(completedAt: LocalDateTime = LocalDateTime.now()) {
        status = PaymentCompletionTaskStatus.SUCCESS
        this.completedAt = completedAt
        lastError = null
        updatedAt = completedAt
    }

    fun markRetry(error: String, nextAttempt: LocalDateTime, now: LocalDateTime = LocalDateTime.now()) {
        retryCount += 1
        nextAttemptAt = nextAttempt
        lastError = error.take(LAST_ERROR_MAX_LENGTH)
        updatedAt = now
    }

    fun markFailed(error: String, now: LocalDateTime = LocalDateTime.now()) {
        status = PaymentCompletionTaskStatus.FAILED
        lastError = error.take(LAST_ERROR_MAX_LENGTH)
        updatedAt = now
    }

    companion object {
        private const val LAST_ERROR_MAX_LENGTH = 1000

        fun pending(
            orderId: Long,
            paymentId: Long,
            paymentKey: String,
            amount: BigDecimal,
            approvedAt: LocalDateTime,
            reason: String,
            now: LocalDateTime = LocalDateTime.now()
        ): PaymentCompletionTask = PaymentCompletionTask(
            orderId = orderId,
            paymentId = paymentId,
            paymentKey = paymentKey,
            amount = amount,
            approvedAt = approvedAt,
            reason = reason.take(500),
            nextAttemptAt = now,
            createdAt = now,
            updatedAt = now
        )
    }
}
