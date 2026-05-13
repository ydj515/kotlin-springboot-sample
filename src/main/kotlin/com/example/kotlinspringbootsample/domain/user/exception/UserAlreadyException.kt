package com.example.kotlinspringbootsample.domain.user.exception

class UserAlreadyException(
    message: String = "user already exists"
) : UserException(
    message = message,
    statusCode = 409,
    errorCode = "USER_ALREADY_EXISTS"
)
