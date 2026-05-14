package com.example.kotlinspringbootsample.presentation.auth.response

data class LoginResponse(
    val username: String,
    val tokenType: String,
    val accessToken: String,
    val accessTokenExpiresAt: Long
)
