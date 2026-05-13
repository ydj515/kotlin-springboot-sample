package com.example.kotlinspringbootsample.common.error

abstract class BusinessException(
    val statusCode: Int,
    val errorCode: String,
    message: String
) : RuntimeException(message)
