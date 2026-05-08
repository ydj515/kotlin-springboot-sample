package com.example.kotlinspringbootsample.domain.order.exception

class OrderNotFoundException(
    message: String = "order not found"
) : RuntimeException(message)
