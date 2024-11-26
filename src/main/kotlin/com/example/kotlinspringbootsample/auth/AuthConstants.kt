package com.example.kotlinspringbootsample.auth

object AuthConstants {
    const val BEARER_PREFIX = "Bearer "
    const val AUTH_PREFIX = "auth"
    const val EXPIRES_KEY = "expires_in"
    const val REFRESH_TOKEN_PATH = "/auth/refresh" // 사용되는 경로 제한
    const val MAX_AGE = 60 * 60 * 24 * 7 // 7일
}