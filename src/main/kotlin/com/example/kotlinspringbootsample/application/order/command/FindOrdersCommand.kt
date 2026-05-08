package com.example.kotlinspringbootsample.application.order.command

import com.example.kotlinspringbootsample.domain.order.OrderStatus

data class FindOrdersCommand(
    val page: Int,
    val size: Int,
    val buyerUsername: String? = null,
    val status: OrderStatus? = null,
    val searchMode: OrderSearchMode = OrderSearchMode.DERIVED
)
