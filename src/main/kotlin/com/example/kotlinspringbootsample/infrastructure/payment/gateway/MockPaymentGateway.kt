package com.example.kotlinspringbootsample.infrastructure.payment.gateway

import com.example.kotlinspringbootsample.domain.payment.gateway.ApproveResult
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import com.example.kotlinspringbootsample.domain.payment.gateway.RefundResult
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Component
class MockPaymentGateway : PaymentGateway {
    override fun approve(amount: BigDecimal, idempotencyKey: String): ApproveResult =
        ApproveResult(
            paymentKey = "MOCK-PG-${UUID.randomUUID()}",
            approvedAt = LocalDateTime.now()
        )

    override fun refund(paymentKey: String, amount: BigDecimal): RefundResult =
        RefundResult(refundedAt = LocalDateTime.now())
}
