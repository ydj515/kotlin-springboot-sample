package com.example.kotlinspringbootsample.domain.payment.service

import com.example.kotlinspringbootsample.domain.payment.Payment
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentNotFoundException
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
import org.springframework.stereotype.Component

@Component
class PaymentLookupService(
    private val paymentRepository: PaymentRepository
) {
    fun requireById(id: Long?): Payment {
        if (id == null) {
            throw PaymentNotFoundException("payment id is required")
        }
        return paymentRepository.findById(id).orElseThrow {
            PaymentNotFoundException("payment not found with id $id")
        }
    }

    fun findByIdempotencyKey(idempotencyKey: String): Payment? =
        paymentRepository.findByIdempotencyKey(idempotencyKey)
}
