package com.example.kotlinspringbootsample.auth.dto

data class LoginResponse(
    val userId: String,
    val type: String,
    val accessToken: String,
    val refreshToken: String?, // TODO
    val accessTokenExpired: Long,
    val refreshTokenExpired: Long?, // TODO
)
