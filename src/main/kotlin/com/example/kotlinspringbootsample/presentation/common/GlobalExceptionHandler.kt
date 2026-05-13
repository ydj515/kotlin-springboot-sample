package com.example.kotlinspringbootsample.presentation.common

import com.example.kotlinspringbootsample.domain.order.exception.InvalidOrderItemException
import com.example.kotlinspringbootsample.domain.order.exception.InvalidOrderStatusTransitionException
import com.example.kotlinspringbootsample.domain.order.exception.OrderNotFoundException
import com.example.kotlinspringbootsample.domain.user.exception.UserAlreadyException
import com.example.kotlinspringbootsample.domain.user.exception.UserException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

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

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.INVALID_REQUEST.status).body(
            ApiResult.failure(
                resultCode = ResultCode.INVALID_REQUEST,
                message = ResultCode.INVALID_REQUEST.message,
                errors = mapOf(
                    ex.name to "Failed to convert value to ${ex.requiredType?.simpleName ?: "required type"}"
                )
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

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleOrderNotFound(ex: OrderNotFoundException): ResponseEntity<ApiResult.Failure> =
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

    @ExceptionHandler(InvalidOrderItemException::class)
    fun handleInvalidOrderItem(ex: InvalidOrderItemException): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.INVALID_REQUEST.status).body(
            ApiResult.failure(
                resultCode = ResultCode.INVALID_REQUEST,
                message = ex.message ?: ResultCode.INVALID_REQUEST.message
            )
        )

    @ExceptionHandler(InvalidOrderStatusTransitionException::class)
    fun handleInvalidOrderStatusTransition(ex: InvalidOrderStatusTransitionException): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.CONFLICT.status).body(
            ApiResult.failure(
                resultCode = ResultCode.CONFLICT,
                message = ex.message ?: ResultCode.CONFLICT.message
            )
        )

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailure(@Suppress("UNUSED_PARAMETER") ex: OptimisticLockingFailureException): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.CONFLICT.status).body(
            ApiResult.failure(
                resultCode = ResultCode.CONFLICT,
                message = "order was modified concurrently. retry the request"
            )
        )
}
