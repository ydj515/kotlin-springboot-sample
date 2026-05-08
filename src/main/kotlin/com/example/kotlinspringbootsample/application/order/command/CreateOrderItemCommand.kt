package com.example.kotlinspringbootsample.application.order.command

import java.math.BigDecimal

data class CreateOrderItemCommand(
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)
