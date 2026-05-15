package com.example.kotlinspringbootsample.application.order.command

data class CancelOrderCommand(
    val id: Long,
    val idempotencyKey: String,
    val reason: String? = null
)
