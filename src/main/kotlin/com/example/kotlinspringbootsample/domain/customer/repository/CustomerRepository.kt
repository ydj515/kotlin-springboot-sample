package com.example.kotlinspringbootsample.domain.customer.repository

import com.example.kotlinspringbootsample.domain.customer.Customer
import org.springframework.data.jpa.repository.JpaRepository

interface CustomerRepository : JpaRepository<Customer, Long> {
    fun findByName(name: String): Customer?
}
