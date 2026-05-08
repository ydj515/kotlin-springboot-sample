package com.example.kotlinspringbootsample.presentation.order.response

import com.example.kotlinspringbootsample.domain.order.OrderStatus

data class OrderStatusSummaryResponse(
    val status: OrderStatus,
    val count: Long
)
