package com.example.kotlinspringbootsample.domain.order

import java.math.BigDecimal

data class OrderLineDraft(
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)
