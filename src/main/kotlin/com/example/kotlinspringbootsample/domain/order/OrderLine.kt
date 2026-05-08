package com.example.kotlinspringbootsample.domain.order

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "order_lines")
class OrderLine(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var productName: String,

    @Column(nullable = false)
    var quantity: Int,

    @Column(nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null
) {
    fun totalPrice(): BigDecimal = unitPrice.multiply(quantity.toBigDecimal())

    companion object {
        fun from(draft: OrderLineDraft): OrderLine =
            OrderLine(
                productName = draft.productName,
                quantity = draft.quantity,
                unitPrice = draft.unitPrice
            )
    }
}
