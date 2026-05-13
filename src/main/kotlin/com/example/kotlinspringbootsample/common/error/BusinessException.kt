package com.example.kotlinspringbootsample.common.error

import com.example.kotlinspringbootsample.presentation.common.ResultCode
import org.springframework.http.HttpStatus

abstract class BusinessException(
    val status: HttpStatus,
    val resultCode: ResultCode,
    message: String = resultCode.message
) : RuntimeException(message)
