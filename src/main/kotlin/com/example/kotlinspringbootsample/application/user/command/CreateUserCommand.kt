package com.example.kotlinspringbootsample.application.user.command

import com.example.kotlinspringbootsample.domain.user.UserType

data class CreateUserCommand(
    val username: String,
    val password: String,
    val name: String? = null,
    val email: String? = null,
    val userType: UserType? = null,
    val trialCount: Int = 0
)
