package com.example.kotlinspringbootsample.presentation.customer.response

import com.example.kotlinspringbootsample.application.customer.result.CustomerResult
import java.time.LocalDateTime

data class CustomerResponse(
    val id: Long?,
    val name: String,
    val email: String,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(result: CustomerResult): CustomerResponse = CustomerResponse(
            id = result.id,
            name = result.name,
            email = result.email,
            createdAt = result.createdAt,
            updatedAt = result.updatedAt
        )
    }
}
