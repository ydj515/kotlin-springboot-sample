package com.example.kotlinspringbootsample.application.customer.result

import com.example.kotlinspringbootsample.domain.customer.Customer
import java.time.LocalDateTime

data class CustomerResult(
    val id: Long?,
    val name: String,
    val email: String,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(customer: Customer): CustomerResult = CustomerResult(
            id = customer.id,
            name = customer.name,
            email = customer.email,
            createdAt = customer.createdAt,
            updatedAt = customer.updatedAt
        )
    }
}
