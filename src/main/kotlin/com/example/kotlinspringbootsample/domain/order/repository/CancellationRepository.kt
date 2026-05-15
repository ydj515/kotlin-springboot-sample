package com.example.kotlinspringbootsample.domain.order.repository

import com.example.kotlinspringbootsample.domain.order.Cancellation
import org.springframework.data.jpa.repository.JpaRepository

interface CancellationRepository : JpaRepository<Cancellation, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Cancellation?

    fun findByOrderId(orderId: Long): List<Cancellation>
}
