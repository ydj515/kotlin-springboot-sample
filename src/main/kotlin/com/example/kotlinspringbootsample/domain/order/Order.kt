package com.example.kotlinspringbootsample.domain.order

import com.example.kotlinspringbootsample.common.entity.BaseEntity
import com.example.kotlinspringbootsample.domain.customer.Customer
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "purchase_orders")
class Order(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    var customer: Customer,

    @Column(name = "order_no", nullable = false, unique = true, length = 50)
    var orderNo: String,

    @Embedded
    var shippingAddress: ShippingAddress,

    @Version
    var version: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 30)
    var status: OrderStatus = OrderStatus.CREATED,

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "ordered_at", nullable = false)
    var orderedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "delivery_requested_at")
    var deliveryRequestedAt: LocalDateTime? = null,

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = null,

    @Column(name = "shipped_at")
    var shippedAt: LocalDateTime? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: LocalDateTime? = null,

    @Column(name = "tracking_number", length = 100)
    var trackingNumber: String? = null,

    @Column(name = "cancel_reason", length = 255)
    var cancelReason: String? = null,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val orderLines: MutableList<OrderLine> = mutableListOf(),

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
) : BaseEntity() {

    val lines: List<OrderLine>
        get() = orderLines.toList()

    fun addLine(draft: OrderLineDraft) = apply {
        orderLines += OrderLine.from(draft).also { it.order = this }
        recalculateTotalAmount()
    }

    fun replaceLines(drafts: List<OrderLineDraft>) = apply {
        orderLines.clear()
        drafts.forEach(::addLine)
        recalculateTotalAmount()
    }

    fun markPaid(paidAt: LocalDateTime = LocalDateTime.now()) {
        status = OrderStatus.PAID
        this.paidAt = paidAt
    }

    fun markShipped(
        shippedAt: LocalDateTime = LocalDateTime.now(),
        trackingNumber: String? = null
    ) {
        status = OrderStatus.SHIPPED
        this.shippedAt = shippedAt
        if (trackingNumber != null) {
            this.trackingNumber = trackingNumber
        }
    }

    fun cancel(
        cancelledAt: LocalDateTime = LocalDateTime.now(),
        reason: String? = null
    ) {
        status = OrderStatus.CANCELLED
        this.cancelledAt = cancelledAt
        if (reason != null) {
            this.cancelReason = reason
        }
    }

    fun markDeleted(deletedAt: LocalDateTime = LocalDateTime.now()) {
        this.deletedAt = deletedAt
    }

    private fun recalculateTotalAmount() {
        totalAmount = orderLines.fold(BigDecimal.ZERO) { amount, line ->
            amount + line.lineAmount
        }
    }
}
