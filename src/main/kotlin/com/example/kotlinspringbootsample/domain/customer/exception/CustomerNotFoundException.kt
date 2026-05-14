package com.example.kotlinspringbootsample.domain.customer.exception

import com.example.kotlinspringbootsample.common.error.BusinessException

class CustomerNotFoundException(
    message: String = "customer not found"
) : BusinessException(
    statusCode = 404,
    errorCode = "CUSTOMER_NOT_FOUND",
    message = message
)
