package com.example.kotlinspringbootsample.domain.order

import com.example.kotlinspringbootsample.common.entity.BaseEntity
import com.example.kotlinspringbootsample.domain.order.exception.IllegalCancellationStateTransitionException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(name = "cancellations")
class Cancellation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "order_id", nullable = false)
    var orderId: Long,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    var idempotencyKey: String,

    @Column(length = 500)
    var reason: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: CancellationStatus = CancellationStatus.REQUESTED,

    @Column(name = "refunded_at")
    var refundedAt: LocalDateTime? = null,

    @Version
    var version: Long? = null
) : BaseEntity() {

    fun markSucceeded(refundedAt: LocalDateTime?) {
        ensureTransition(
            CancellationStatus.SUCCEEDED,
            allowedFrom = setOf(CancellationStatus.REQUESTED, CancellationStatus.REFUND_FAILED)
        )
        this.refundedAt = refundedAt
        this.status = CancellationStatus.SUCCEEDED
    }

    fun markRefundFailed() {
        ensureTransition(
            CancellationStatus.REFUND_FAILED,
            allowedFrom = setOf(CancellationStatus.REQUESTED)
        )
        this.status = CancellationStatus.REFUND_FAILED
    }

    private fun ensureTransition(target: CancellationStatus, allowedFrom: Set<CancellationStatus>) {
        if (status !in allowedFrom) {
            throw IllegalCancellationStateTransitionException(status, target)
        }
    }

    companion object {
        fun requested(
            orderId: Long,
            idempotencyKey: String,
            reason: String?
        ): Cancellation = Cancellation(
            orderId = orderId,
            idempotencyKey = idempotencyKey,
            reason = reason,
            status = CancellationStatus.REQUESTED
        )
    }
}
