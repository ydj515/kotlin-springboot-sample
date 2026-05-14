package com.example.kotlinspringbootsample.domain.payment.exception

import com.example.kotlinspringbootsample.common.error.BusinessException
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus

class IllegalPaymentStateTransitionException(
    from: PaymentStatus,
    to: PaymentStatus
) : BusinessException(
    statusCode = 409,
    errorCode = "ILLEGAL_PAYMENT_STATE_TRANSITION",
    message = "cannot transit payment from $from to $to"
)
