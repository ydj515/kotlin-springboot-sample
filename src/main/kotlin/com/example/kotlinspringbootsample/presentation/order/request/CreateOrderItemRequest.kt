package com.example.kotlinspringbootsample.presentation.order.request

import java.math.BigDecimal

data class CreateOrderItemRequest(
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)
