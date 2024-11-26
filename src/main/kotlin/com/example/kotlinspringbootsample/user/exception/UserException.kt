package com.example.kotlinspringbootsample.user.exception

open class UserException(
    override val message: String = "User exception"
) : RuntimeException(message)