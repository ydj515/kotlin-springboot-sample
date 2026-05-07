package com.example.kotlinspringbootsample.common.aop

import com.example.kotlinspringbootsample.common.dto.ApiResult
import com.example.kotlinspringbootsample.common.dto.ResultCode
import com.example.kotlinspringbootsample.post.exception.PostNotFoundException
import com.example.kotlinspringbootsample.user.exception.UserAlreadyException
import com.example.kotlinspringbootsample.user.exception.UserException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.INVALID_REQUEST.status).body(
            ApiResult.failure(
                resultCode = ResultCode.INVALID_REQUEST,
                errors = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
            )
        )

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.INTERNAL_ERROR.status).body(
            ApiResult.failure(
                resultCode = ResultCode.INTERNAL_ERROR,
                message = ex.message ?: ResultCode.INTERNAL_ERROR.message
            )
        )

    @ExceptionHandler(PostNotFoundException::class)
    fun handlePostNotFound(ex: PostNotFoundException): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.NOT_FOUND.status).body(
            ApiResult.failure(
                resultCode = ResultCode.NOT_FOUND,
                message = ex.message ?: ResultCode.NOT_FOUND.message
            )
        )

    @ExceptionHandler(UserAlreadyException::class)
    fun handleUserAlreadyExists(ex: UserAlreadyException): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.ALREADY_EXISTS.status).body(
            ApiResult.failure(
                resultCode = ResultCode.ALREADY_EXISTS,
                message = ex.message ?: ResultCode.ALREADY_EXISTS.message
            )
        )

    @ExceptionHandler(UserException::class)
    fun handleUserException(ex: UserException): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.INVALID_REQUEST.status).body(
            ApiResult.failure(
                resultCode = ResultCode.INVALID_REQUEST,
                message = ex.message ?: ResultCode.INVALID_REQUEST.message
            )
        )
}
