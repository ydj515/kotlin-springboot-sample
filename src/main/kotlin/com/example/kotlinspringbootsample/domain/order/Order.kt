package com.example.kotlinspringbootsample.domain.order

import com.example.kotlinspringbootsample.common.entity.BaseEntity
import com.example.kotlinspringbootsample.domain.user.User
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class Order(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    var buyer: User,

    @Embedded
    var shippingAddress: ShippingAddress,

    @Version
    var version: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.CREATED,

    @Column(nullable = false, precision = 12, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = null,

    @Column(name = "shipped_at")
    var shippedAt: LocalDateTime? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: LocalDateTime? = null,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val orderLines: MutableList<OrderLine> = mutableListOf(),

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

    fun markShipped(shippedAt: LocalDateTime = LocalDateTime.now()) {
        status = OrderStatus.SHIPPED
        this.shippedAt = shippedAt
    }

    fun cancel(cancelledAt: LocalDateTime = LocalDateTime.now()) {
        status = OrderStatus.CANCELLED
        this.cancelledAt = cancelledAt
    }

    fun markDeleted(deletedAt: LocalDateTime = LocalDateTime.now()) {
        this.deletedAt = deletedAt
    }

    private fun recalculateTotalAmount() {
        totalAmount = orderLines.fold(BigDecimal.ZERO) { amount, line ->
            amount + line.totalPrice()
        }
    }
}
