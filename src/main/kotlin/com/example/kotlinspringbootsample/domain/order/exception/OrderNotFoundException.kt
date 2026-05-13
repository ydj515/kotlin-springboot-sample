package com.example.kotlinspringbootsample.domain.order.exception

import com.example.kotlinspringbootsample.common.error.BusinessException
import com.example.kotlinspringbootsample.presentation.common.ResultCode
import org.springframework.http.HttpStatus

class OrderNotFoundException(
    message: String = "order not found"
) : BusinessException(
    status = HttpStatus.NOT_FOUND,
    resultCode = ResultCode.NOT_FOUND,
    message = message
)
