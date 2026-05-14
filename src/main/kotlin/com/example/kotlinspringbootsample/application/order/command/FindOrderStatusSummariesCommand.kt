package com.example.kotlinspringbootsample.application.order.command

import com.example.kotlinspringbootsample.domain.order.OrderStatus

data class FindOrderStatusSummariesCommand(
    val customerName: String? = null,
    val status: OrderStatus? = null
)
