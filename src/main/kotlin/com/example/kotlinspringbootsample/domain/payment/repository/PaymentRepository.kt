package com.example.kotlinspringbootsample.domain.payment.repository

import com.example.kotlinspringbootsample.domain.payment.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Payment?

    fun findByOrderId(orderId: Long): List<Payment>
}
