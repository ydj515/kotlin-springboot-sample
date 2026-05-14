package com.example.kotlinspringbootsample.domain.payment.exception

import com.example.kotlinspringbootsample.common.error.BusinessException

class IdempotencyKeyInvalidException(
    message: String = "Idempotency-Key header has invalid format"
) : BusinessException(
    statusCode = 400,
    errorCode = "IDEMPOTENCY_KEY_INVALID",
    message = message
)
