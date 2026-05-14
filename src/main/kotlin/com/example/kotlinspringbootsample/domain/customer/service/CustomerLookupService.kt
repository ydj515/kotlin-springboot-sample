package com.example.kotlinspringbootsample.domain.customer.service

import com.example.kotlinspringbootsample.domain.customer.Customer
import com.example.kotlinspringbootsample.domain.customer.exception.CustomerNotFoundException
import com.example.kotlinspringbootsample.domain.customer.repository.CustomerRepository
import org.springframework.stereotype.Component

@Component
class CustomerLookupService(
    private val customerRepository: CustomerRepository
) {
    fun requireById(id: Long?): Customer {
        if (id == null) {
            throw CustomerNotFoundException("customer id is required")
        }
        return customerRepository.findById(id).orElseThrow {
            CustomerNotFoundException("customer not found with id $id")
        }
    }
}
