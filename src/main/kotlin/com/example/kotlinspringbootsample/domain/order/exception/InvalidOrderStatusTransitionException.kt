package com.example.kotlinspringbootsample.domain.order.exception

class InvalidOrderStatusTransitionException(
    message: String
) : RuntimeException(message)
