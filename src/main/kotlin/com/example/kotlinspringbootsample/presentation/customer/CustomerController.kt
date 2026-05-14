package com.example.kotlinspringbootsample.presentation.customer

import com.example.kotlinspringbootsample.application.customer.CustomerUseCase
import com.example.kotlinspringbootsample.application.customer.command.FindCustomersCommand
import com.example.kotlinspringbootsample.application.customer.command.GetCustomerCommand
import com.example.kotlinspringbootsample.presentation.common.ApiResult
import com.example.kotlinspringbootsample.presentation.customer.response.CustomerResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "주문 고객 조회 API")
class CustomerController(private val customerUseCase: CustomerUseCase) {

    @Operation(summary = "고객 목록 조회", description = "전체 고객 목록을 조회합니다.")
    @GetMapping
    fun getCustomers(): ApiResult.Success<List<CustomerResponse>> =
        ApiResult.success(
            customerUseCase.findAll(FindCustomersCommand.empty()).map(CustomerResponse::from)
        )

    @Operation(summary = "고객 단건 조회", description = "id로 고객 단건을 조회합니다.")
    @GetMapping("/{id}")
    fun getCustomer(@PathVariable id: Long): ApiResult.Success<CustomerResponse> =
        ApiResult.success(CustomerResponse.from(customerUseCase.findById(GetCustomerCommand(id))))
}
