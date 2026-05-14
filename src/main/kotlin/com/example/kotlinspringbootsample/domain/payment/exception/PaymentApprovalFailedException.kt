package com.example.kotlinspringbootsample.domain.payment.exception

import com.example.kotlinspringbootsample.common.error.BusinessException

class PaymentApprovalFailedException(
    message: String = "payment approval failed"
) : BusinessException(
    statusCode = 422,
    errorCode = "PAYMENT_APPROVAL_FAILED",
    message = message
)
