package com.example.kotlinspringbootsample.domain.order.exception

import com.example.kotlinspringbootsample.common.error.BusinessException

class InvalidOrderStatusTransitionException(
    message: String
) : BusinessException(
    statusCode = 409,
    errorCode = "409",
    message = message
)
