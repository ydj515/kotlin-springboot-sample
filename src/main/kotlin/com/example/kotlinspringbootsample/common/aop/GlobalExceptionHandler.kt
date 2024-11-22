package com.example.kotlinspringbootsample.common.aop

import com.example.kotlinspringbootsample.common.dto.ErrorResponse
import com.example.kotlinspringbootsample.common.dto.ResultCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception): ResponseEntity<ErrorResponse<String?>> {
        val errorResponse = ErrorResponse.of(ex.message, ResultCode.INTERNAL_ERROR)
        return ResponseEntity.internalServerError().body(errorResponse)
    }
}
