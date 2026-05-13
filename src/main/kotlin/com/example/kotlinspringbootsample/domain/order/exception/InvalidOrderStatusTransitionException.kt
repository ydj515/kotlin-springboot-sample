package com.example.kotlinspringbootsample.domain.order.exception

import com.example.kotlinspringbootsample.common.error.BusinessException
import com.example.kotlinspringbootsample.presentation.common.ResultCode
import org.springframework.http.HttpStatus

class InvalidOrderStatusTransitionException(
    message: String
) : BusinessException(
    status = HttpStatus.CONFLICT,
    resultCode = ResultCode.CONFLICT,
    message = message
)
