package com.example.kotlinspringbootsample.domain.order.exception

import com.example.kotlinspringbootsample.common.error.BusinessException
import com.example.kotlinspringbootsample.domain.order.CancellationStatus

class IllegalCancellationStateTransitionException(
    from: CancellationStatus,
    to: CancellationStatus
) : BusinessException(
    statusCode = 409,
    errorCode = "ILLEGAL_CANCELLATION_STATE_TRANSITION",
    message = "cannot transit cancellation from $from to $to"
)
