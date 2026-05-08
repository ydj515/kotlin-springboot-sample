package com.example.kotlinspringbootsample.domain.order

import jakarta.persistence.Embeddable

@Embeddable
data class ShippingAddress(
    val recipient: String = "",
    val zipCode: String = "",
    val address1: String = "",
    val address2: String = ""
)
