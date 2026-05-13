package com.example.kotlinspringbootsample.domain.order.exception

import com.example.kotlinspringbootsample.common.error.BusinessException
import com.example.kotlinspringbootsample.presentation.common.ResultCode
import org.springframework.http.HttpStatus

class InvalidOrderItemException(
    message: String
) : BusinessException(
    status = HttpStatus.BAD_REQUEST,
    resultCode = ResultCode.INVALID_REQUEST,
    message = message
)
