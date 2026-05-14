package com.example.kotlinspringbootsample.domain.payment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "payment_histories")
class PaymentHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    var fromStatus: PaymentStatus? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30, nullable = false)
    var toStatus: PaymentStatus,

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: LocalDateTime,

    @Column(length = 255)
    var reason: String? = null
) {
    companion object {
        fun of(
            fromStatus: PaymentStatus?,
            toStatus: PaymentStatus,
            occurredAt: LocalDateTime,
            reason: String? = null
        ): PaymentHistory = PaymentHistory(
            fromStatus = fromStatus,
            toStatus = toStatus,
            occurredAt = occurredAt,
            reason = reason
        )
    }
}
