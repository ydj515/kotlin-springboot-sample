package com.example.kotlinspringbootsample.presentation.order.response

import com.example.kotlinspringbootsample.domain.order.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderSummaryResponse(
    val id: Long,
    val version: Long,
    val orderNo: String,
    val customerId: Long,
    val customerName: String,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val orderedAt: LocalDateTime,
    val paidAt: LocalDateTime?,
    val shippedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val createdAt: LocalDateTime
)
