package com.example.kotlinspringbootsample.domain.user.exception

open class UserException(
    message: String = "User exception"
) : RuntimeException(message)
