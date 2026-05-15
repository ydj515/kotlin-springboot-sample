package com.example.kotlinspringbootsample.domain.order.event

import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderPaidEvent(
    val eventId: String,
    val orderId: Long,
    val paymentId: Long,
    val paymentKey: String,
    val amount: BigDecimal,
    val paidAt: LocalDateTime
) {
    companion object {
        const val EVENT_TYPE = "OrderPaidEvent"
        const val AGGREGATE_TYPE = "Order"
    }
}
