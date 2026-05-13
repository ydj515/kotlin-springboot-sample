package com.example.kotlinspringbootsample.domain.order.exception

import com.example.kotlinspringbootsample.common.error.BusinessException

class InvalidOrderItemException(
    message: String
) : BusinessException(
    statusCode = 400,
    errorCode = "400",
    message = message
)
