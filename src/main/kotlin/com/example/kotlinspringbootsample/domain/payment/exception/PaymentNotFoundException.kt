package com.example.kotlinspringbootsample.domain.payment.exception

import com.example.kotlinspringbootsample.common.error.BusinessException

class PaymentNotFoundException(
    message: String = "payment not found"
) : BusinessException(
    statusCode = 404,
    errorCode = "PAYMENT_NOT_FOUND",
    message = message
)
