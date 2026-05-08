package com.example.kotlinspringbootsample.presentation.order.request

data class CreateOrderRequest(
    val buyerUsername: String,
    val shippingAddress: ShippingAddressRequest,
    val items: List<CreateOrderItemRequest>
)
