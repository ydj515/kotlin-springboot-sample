package com.example.kotlinspringbootsample.user.exception

class UserAlreadyException(
    message: String = "user already exists"
) : UserException(message)
