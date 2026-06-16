package com.example.kotlinspringbootsample.application.order.result

import com.example.kotlinspringbootsample.domain.order.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderResult(
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
    val items: List<OrderLineResult>,
    val orderedAt: LocalDateTime,
    val deliveryRequestedAt: LocalDateTime?,
    val paidAt: LocalDateTime?,
    val paymentCompletionPendingAt: LocalDateTime? = null,
    val paymentCompletionFailureReason: String? = null,
    val shippedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val trackingNumber: String?,
    val cancelReason: String?,
    val paymentKey: String? = null,
    val createdAt: LocalDateTime
)
