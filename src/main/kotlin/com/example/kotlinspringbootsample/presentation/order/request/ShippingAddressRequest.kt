package com.example.kotlinspringbootsample.presentation.order.request

data class ShippingAddressRequest(
    val recipient: String,
    val zipCode: String,
    val address1: String,
    val address2: String
)
