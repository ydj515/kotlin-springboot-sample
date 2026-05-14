package com.example.kotlinspringbootsample.domain.payment.exception

import com.example.kotlinspringbootsample.common.error.BusinessException

class IdempotencyKeyRequiredException(
    message: String = "Idempotency-Key header is required"
) : BusinessException(
    statusCode = 400,
    errorCode = "IDEMPOTENCY_KEY_REQUIRED",
    message = message
)
