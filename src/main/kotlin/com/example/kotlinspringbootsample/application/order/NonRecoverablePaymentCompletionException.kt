package com.example.kotlinspringbootsample.application.order

class NonRecoverablePaymentCompletionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
