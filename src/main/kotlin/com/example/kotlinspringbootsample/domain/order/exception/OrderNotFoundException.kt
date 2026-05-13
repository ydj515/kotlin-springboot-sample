package com.example.kotlinspringbootsample.domain.order.exception

import com.example.kotlinspringbootsample.common.error.BusinessException

class OrderNotFoundException(
    message: String = "order not found"
) : BusinessException(
    statusCode = 404,
    errorCode = "404",
    message = message
)
