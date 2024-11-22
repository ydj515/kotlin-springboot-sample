package com.example.kotlinspringbootsample.common.dto

import java.time.LocalDateTime

data class ApiResponse<T>(
    val code: String,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val data: T?
) {
    companion object {
        fun <T> success(data: T, resultCode: ResultCode): ApiResponse<T> {
            return ApiResponse(
                code = resultCode.code,
                message = resultCode.message,
                data = data
            )
        }

        fun <T> error(resultCode: ResultCode, data: T? = null): ApiResponse<T> {
            return ApiResponse(
                code = resultCode.code,
                message = resultCode.message,
                data = data
            )
        }
    }
}
