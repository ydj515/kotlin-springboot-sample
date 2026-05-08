package com.example.kotlinspringbootsample.application.order.command

data class CreateOrderCommand(
    val buyerUsername: String,
    val recipient: String,
    val zipCode: String,
    val address1: String,
    val address2: String,
    val items: List<CreateOrderItemCommand>
)
