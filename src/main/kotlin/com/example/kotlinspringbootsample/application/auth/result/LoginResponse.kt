package com.example.kotlinspringbootsample.application.auth.result

data class LoginResponse(
    val userId: String,
    val type: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val accessTokenExpired: Long,
    val refreshTokenExpired: Long? = null,
)
