package com.example.kotlinspringbootsample.application.order.result

import com.example.kotlinspringbootsample.domain.order.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderSummaryResult(
    val id: Long,
    val version: Long,
    val buyerUsername: String,
    val status: OrderStatus,
    val totalAmount: BigDecimal,
    val paidAt: LocalDateTime?,
    val shippedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val createdAt: LocalDateTime
)
