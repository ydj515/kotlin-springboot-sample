package com.example.kotlinspringbootsample.presentation.common

import java.time.LocalDateTime

sealed interface ApiResult<out T> {
    val result: String
    val code: String
    val message: String
    val timestamp: LocalDateTime

    data class Success<T>(
        override val code: String,
        override val message: String,
        val data: T,
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : ApiResult<T> {
        override val result: String = "success"
    }

    data class Failure(
        val status: Int,
        override val code: String,
        override val message: String,
        val path: String? = null,
        val traceId: String? = null,
        val errors: Any? = null,
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : ApiResult<Nothing> {
        override val result: String = "failure"
    }

    companion object {
        fun <T> success(
            data: T,
            resultCode: ResultCode = ResultCode.SUCCESS,
            message: String = resultCode.message
        ): Success<T> = Success(
            code = resultCode.code,
            message = message,
            data = data
        )

        fun failure(
            resultCode: ResultCode,
            status: Int = resultCode.status.value(),
            message: String = resultCode.message,
            path: String? = null,
            traceId: String? = null,
            errors: Any? = null
        ): Failure = Failure(
            status = status,
            code = resultCode.code,
            message = message,
            path = path,
            traceId = traceId,
            errors = errors
        )
    }
}
