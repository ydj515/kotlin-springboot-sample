package com.example.kotlinspringbootsample.application.order.result

import com.example.kotlinspringbootsample.domain.order.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderResult(
    val id: Long,
    val version: Long,
    val buyerUsername: String,
    val status: OrderStatus,
    val recipient: String,
    val zipCode: String,
    val address1: String,
    val address2: String,
    val totalAmount: BigDecimal,
    val items: List<OrderLineResult>,
    val paidAt: LocalDateTime?,
    val shippedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val createdAt: LocalDateTime
)
