package com.example.kotlinspringbootsample.domain.order

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "purchase_order_items")
class OrderLine(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "product_name", nullable = false)
    var productName: String,

    @Column(nullable = false)
    var quantity: Int,

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal,

    @Column(name = "line_amount", nullable = false, precision = 12, scale = 2)
    var lineAmount: BigDecimal,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null
) {
    companion object {
        fun from(draft: OrderLineDraft): OrderLine =
            OrderLine(
                productName = draft.productName,
                quantity = draft.quantity,
                unitPrice = draft.unitPrice,
                lineAmount = draft.unitPrice.multiply(draft.quantity.toBigDecimal())
            )
    }
}
