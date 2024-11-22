package com.example.kotlinspringbootsample.common.aop

import com.example.kotlinspringbootsample.common.dto.ErrorResponse
import com.example.kotlinspringbootsample.common.dto.ResultCode
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String?>> {
        val errors = ex.bindingResult.allErrors.associate {
            (it as FieldError).field to it.defaultMessage
        }
        return ResponseEntity.badRequest().body(errors)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception): ResponseEntity<ErrorResponse<String?>> {
        val errorResponse = ErrorResponse.of(ex.message, ResultCode.INTERNAL_ERROR)
        return ResponseEntity.internalServerError().body(errorResponse)
    }
}
