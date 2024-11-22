package com.example.kotlinspringbootsample.user.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:NotBlank(message = "Username cannot be blank")
    @field:Size(min = 4, max = 10, message = "Username must be between 4 and 10 characters")
    @field:Pattern(
        regexp = "^[a-z0-9]+$",
        message = "Username can only contain alphanumeric characters"
    )
    val username: String,

    @field:NotBlank(message = "Password cannot be blank")
    @field:Size(min = 8, max = 15, message = "Password must be between 8 and 15 characters")
    @field:Pattern(
        regexp = "^[a-zA-Z0-9]+$",
        message = "Username can only contain alphanumeric characters"
    )
    val password: String,
)