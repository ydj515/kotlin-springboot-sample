package com.example.kotlinspringbootsample.domain.user.exception

import com.example.kotlinspringbootsample.presentation.common.ResultCode
import org.springframework.http.HttpStatus

class UserAlreadyException(
    message: String = "user already exists"
) : UserException(
    message = message,
    status = HttpStatus.CONFLICT,
    resultCode = ResultCode.USER_ALREADY_EXISTS
)
