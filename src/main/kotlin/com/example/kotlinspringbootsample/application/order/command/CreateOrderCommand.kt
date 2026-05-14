package com.example.kotlinspringbootsample.application.order.command

import java.time.LocalDateTime

data class CreateOrderCommand(
    val customerId: Long,
    val recipient: String,
    val zipCode: String,
    val address1: String,
    val address2: String,
    val deliveryRequestedAt: LocalDateTime? = null,
    val items: List<CreateOrderItemCommand>
)
