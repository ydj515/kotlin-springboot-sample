package com.example.kotlinspringbootsample.domain.payment.exception

import com.example.kotlinspringbootsample.common.error.BusinessException

class IdempotencyConflictException(
    message: String = "idempotency key conflict"
) : BusinessException(
    statusCode = 409,
    errorCode = "IDEMPOTENCY_KEY_CONFLICT",
    message = message
)
