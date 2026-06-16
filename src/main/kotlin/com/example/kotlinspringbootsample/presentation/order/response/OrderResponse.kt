package com.example.kotlinspringbootsample.presentation.order.response

import com.example.kotlinspringbootsample.domain.order.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderResponse(
    val id: Long,
    val version: Long,
    val orderNo: String,
    val customerId: Long,
    val customerName: String,
    val status: OrderStatus,
    val recipient: String,
    val zipCode: String,
    val address1: String,
    val address2: String,
    val totalAmount: BigDecimal,
    val items: List<OrderLineResponse>,
    val orderedAt: LocalDateTime,
    val deliveryRequestedAt: LocalDateTime?,
    val paidAt: LocalDateTime?,
    val paymentCompletionPendingAt: LocalDateTime? = null,
    val shippedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val trackingNumber: String?,
    val cancelReason: String?,
    val paymentKey: String? = null,
    val createdAt: LocalDateTime
)
