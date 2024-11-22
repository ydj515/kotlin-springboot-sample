package com.example.kotlinspringbootsample.common.dto

import java.time.LocalDateTime

// success가 없다면으로 에러를 판단 하기 위해 success 없음
data class ErrorResponse<T>(
    val code: String,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val data: T?
) {
    companion object {
        fun <T> of(data: T, resultCode: ResultCode): ErrorResponse<T> {
            return ErrorResponse(
                code = resultCode.code,
                message = resultCode.message,
                data = data
            )
        }
    }
}