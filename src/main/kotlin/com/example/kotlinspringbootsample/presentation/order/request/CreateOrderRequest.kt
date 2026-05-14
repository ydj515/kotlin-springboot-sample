package com.example.kotlinspringbootsample.presentation.order.request

import java.time.LocalDateTime

data class CreateOrderRequest(
    val customerId: Long,
    val shippingAddress: ShippingAddressRequest,
    val deliveryRequestedAt: LocalDateTime? = null,
    val items: List<CreateOrderItemRequest>
)
