package com.example.kotlinspringbootsample.domain.payment

enum class PaymentStatus {
    REQUESTED,
    APPROVED,
    FAILED,
    REFUNDED,
    REFUND_FAILED
}
