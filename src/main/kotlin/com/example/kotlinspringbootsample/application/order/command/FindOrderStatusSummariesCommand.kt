package com.example.kotlinspringbootsample.application.order.command

import com.example.kotlinspringbootsample.domain.order.OrderStatus

data class FindOrderStatusSummariesCommand(
    val buyerUsername: String? = null,
    val status: OrderStatus? = null
)
