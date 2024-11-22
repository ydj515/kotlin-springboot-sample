package com.example.kotlinspringbootsample.user.exception

class UserAlreadyException(
    override val message: String = "user already exists"
) : UserException(message)