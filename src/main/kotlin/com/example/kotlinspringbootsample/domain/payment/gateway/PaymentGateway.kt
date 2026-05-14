package com.example.kotlinspringbootsample.domain.payment.gateway

import java.math.BigDecimal

interface PaymentGateway {
    fun approve(amount: BigDecimal, idempotencyKey: String): ApproveResult

    fun refund(paymentKey: String, amount: BigDecimal): RefundResult
}
