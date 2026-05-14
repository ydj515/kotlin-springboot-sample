package com.example.kotlinspringbootsample.domain.payment.gateway

import java.time.LocalDateTime

data class RefundResult(
    val refundedAt: LocalDateTime
)
