package com.example.kotlinspringbootsample.domain.user.exception

import com.example.kotlinspringbootsample.common.error.BusinessException
import com.example.kotlinspringbootsample.presentation.common.ResultCode
import org.springframework.http.HttpStatus

open class UserException(
    message: String = "User exception",
    status: HttpStatus = HttpStatus.BAD_REQUEST,
    resultCode: ResultCode = ResultCode.INVALID_REQUEST
) : BusinessException(
    status = status,
    resultCode = resultCode,
    message = message
)
