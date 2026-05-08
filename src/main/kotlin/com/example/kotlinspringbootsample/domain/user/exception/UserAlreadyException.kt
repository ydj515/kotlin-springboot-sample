package com.example.kotlinspringbootsample.domain.user.exception

class UserAlreadyException(
    message: String = "user already exists"
) : UserException(message)
