package com.example.kotlinspringbootsample.domain.user.exception

class UserNotFoundException(
    message: String = "user not found"
) : UserException(
    message = message,
    statusCode = 404,
    errorCode = "USER_NOT_FOUND"
)
