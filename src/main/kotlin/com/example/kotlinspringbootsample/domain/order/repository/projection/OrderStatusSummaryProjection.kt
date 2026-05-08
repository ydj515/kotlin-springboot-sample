package com.example.kotlinspringbootsample.domain.order.repository.projection

import com.example.kotlinspringbootsample.domain.order.OrderStatus

interface OrderStatusSummaryProjection {
    val status: OrderStatus
    val count: Long
}
