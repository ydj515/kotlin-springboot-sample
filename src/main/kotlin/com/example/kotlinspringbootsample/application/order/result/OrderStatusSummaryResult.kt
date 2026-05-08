package com.example.kotlinspringbootsample.application.order.result

import com.example.kotlinspringbootsample.domain.order.OrderStatus

data class OrderStatusSummaryResult(
    val status: OrderStatus,
    val count: Long
)
