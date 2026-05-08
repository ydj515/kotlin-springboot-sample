package com.example.kotlinspringbootsample.presentation.order.response

import java.math.BigDecimal

data class OrderLineResponse(
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineAmount: BigDecimal
)
