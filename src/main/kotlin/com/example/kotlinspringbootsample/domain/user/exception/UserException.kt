package com.example.kotlinspringbootsample.domain.user.exception

import com.example.kotlinspringbootsample.common.error.BusinessException

open class UserException(
    message: String = "User exception",
    statusCode: Int = 400,
    errorCode: String = "400"
) : BusinessException(
    statusCode = statusCode,
    errorCode = errorCode,
    message = message
)
