package com.example.kotlinspringbootsample.application.customer

import com.example.kotlinspringbootsample.application.customer.command.FindCustomersCommand
import com.example.kotlinspringbootsample.application.customer.command.GetCustomerCommand
import com.example.kotlinspringbootsample.application.customer.result.CustomerResult
import com.example.kotlinspringbootsample.domain.customer.repository.CustomerRepository
import com.example.kotlinspringbootsample.domain.customer.service.CustomerLookupService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CustomerUseCase(
    private val customerRepository: CustomerRepository,
    private val customerLookupService: CustomerLookupService
) {
    fun findAll(command: FindCustomersCommand): List<CustomerResult> =
        customerRepository.findAll().map(CustomerResult::from)

    fun findById(command: GetCustomerCommand): CustomerResult =
        CustomerResult.from(customerLookupService.requireById(command.id))
}
