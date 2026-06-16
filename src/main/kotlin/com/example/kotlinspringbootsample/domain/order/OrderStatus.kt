package com.example.kotlinspringbootsample.domain.order

enum class OrderStatus {
    CREATED,
    PAYMENT_COMPLETION_PENDING,
    PAID,
    SHIPPED,
    CANCELLED
}
