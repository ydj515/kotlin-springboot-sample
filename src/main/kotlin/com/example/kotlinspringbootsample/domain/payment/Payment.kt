package com.example.kotlinspringbootsample.domain.payment

import com.example.kotlinspringbootsample.common.entity.BaseEntity
import com.example.kotlinspringbootsample.domain.payment.exception.IllegalPaymentStateTransitionException
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "order_id", nullable = false)
    var orderId: Long,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    var idempotencyKey: String,

    @Column(nullable = false, precision = 12, scale = 2)
    var amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: PaymentStatus = PaymentStatus.REQUESTED,

    @Column(name = "payment_key", length = 100)
    var paymentKey: String? = null,

    @Column(name = "approved_at")
    var approvedAt: LocalDateTime? = null,

    @Column(name = "refunded_at")
    var refundedAt: LocalDateTime? = null,

    @Version
    var version: Long? = null,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "payment_id")
    private val historyList: MutableList<PaymentHistory> = mutableListOf()
) : BaseEntity() {

    val histories: List<PaymentHistory>
        get() = historyList.toList()

    fun markApproved(paymentKey: String, approvedAt: LocalDateTime, reason: String = "PG approved") {
        ensureTransition(PaymentStatus.APPROVED, allowedFrom = setOf(PaymentStatus.REQUESTED))
        this.paymentKey = paymentKey
        this.approvedAt = approvedAt
        recordTransition(PaymentStatus.APPROVED, approvedAt, reason)
    }

    fun markFailed(reason: String, occurredAt: LocalDateTime = LocalDateTime.now()) {
        ensureTransition(PaymentStatus.FAILED, allowedFrom = setOf(PaymentStatus.REQUESTED))
        recordTransition(PaymentStatus.FAILED, occurredAt, reason)
    }

    fun markRefunded(refundedAt: LocalDateTime, reason: String = "PG refunded") {
        ensureTransition(
            PaymentStatus.REFUNDED,
            allowedFrom = setOf(PaymentStatus.APPROVED, PaymentStatus.REFUND_FAILED)
        )
        this.refundedAt = refundedAt
        recordTransition(PaymentStatus.REFUNDED, refundedAt, reason)
    }

    fun markRefundFailed(reason: String, occurredAt: LocalDateTime = LocalDateTime.now()) {
        ensureTransition(PaymentStatus.REFUND_FAILED, allowedFrom = setOf(PaymentStatus.APPROVED))
        recordTransition(PaymentStatus.REFUND_FAILED, occurredAt, reason)
    }

    private fun ensureTransition(target: PaymentStatus, allowedFrom: Set<PaymentStatus>) {
        if (status !in allowedFrom) {
            throw IllegalPaymentStateTransitionException(status, target)
        }
    }

    private fun recordTransition(target: PaymentStatus, occurredAt: LocalDateTime, reason: String) {
        val previous = status
        status = target
        historyList += PaymentHistory.of(
            fromStatus = previous,
            toStatus = target,
            occurredAt = occurredAt,
            reason = reason
        )
    }

    companion object {
        fun request(
            orderId: Long,
            idempotencyKey: String,
            amount: BigDecimal,
            requestedAt: LocalDateTime = LocalDateTime.now(),
            reason: String = "payment requested"
        ): Payment {
            val payment = Payment(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                amount = amount,
                status = PaymentStatus.REQUESTED
            )
            payment.historyList += PaymentHistory.of(
                fromStatus = null,
                toStatus = PaymentStatus.REQUESTED,
                occurredAt = requestedAt,
                reason = reason
            )
            return payment
        }
    }
}
