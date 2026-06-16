package com.example.kotlinspringbootsample.presentation.order.response

enum class PayOrderResponseStatus {
    PAID,
    PROCESSING,
    CANCELING,
    CANCELED
}

data class PayOrderResponse(
    val status: PayOrderResponseStatus,
    val message: String,
    val pollingUrl: String,
    val order: OrderResponse
)
