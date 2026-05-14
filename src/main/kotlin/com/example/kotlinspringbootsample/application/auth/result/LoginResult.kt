package com.example.kotlinspringbootsample.application.auth.result

data class LoginResult(
    val username: String,
    val tokenType: String,
    val accessToken: String,
    val accessTokenExpiresAt: Long
)
