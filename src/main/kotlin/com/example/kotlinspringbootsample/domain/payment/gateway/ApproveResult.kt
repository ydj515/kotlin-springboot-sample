package com.example.kotlinspringbootsample.domain.payment.gateway

import java.time.LocalDateTime

data class ApproveResult(
    val paymentKey: String,
    val approvedAt: LocalDateTime
)
