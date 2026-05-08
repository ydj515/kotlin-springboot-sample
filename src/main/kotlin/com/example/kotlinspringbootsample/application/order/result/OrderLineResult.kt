package com.example.kotlinspringbootsample.application.order.result

import java.math.BigDecimal

data class OrderLineResult(
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineAmount: BigDecimal
)
