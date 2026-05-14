package com.example.kotlinspringbootsample.presentation.common

import com.example.kotlinspringbootsample.common.error.BusinessException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.INVALID_REQUEST.status).body(
            failureBody(
                resultCode = ResultCode.INVALID_REQUEST,
                request = request,
                errors = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
            )
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.INVALID_REQUEST.status).body(
            failureBody(
                resultCode = ResultCode.INVALID_REQUEST,
                request = request,
                message = ResultCode.INVALID_REQUEST.message,
                errors = mapOf(
                    ex.name to "Failed to convert value to ${ex.requiredType?.simpleName ?: "required type"}"
                )
            )
        )

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingRequestHeader(
        ex: MissingRequestHeaderException,
        request: HttpServletRequest
    ): ResponseEntity<ApiResult.Failure> {
        val resultCode = if (ex.headerName.equals("Idempotency-Key", ignoreCase = true)) {
            ResultCode.IDEMPOTENCY_KEY_REQUIRED
        } else {
            ResultCode.INVALID_REQUEST
        }
        return ResponseEntity.status(resultCode.status).body(
            failureBody(
                resultCode = resultCode,
                request = request,
                message = resultCode.message
            )
        )
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: HttpServletRequest
    ): ResponseEntity<ApiResult.Failure> {
        val status = HttpStatus.resolve(ex.statusCode) ?: HttpStatus.INTERNAL_SERVER_ERROR
        val resultCode = ResultCode.entries.firstOrNull { it.code == ex.errorCode } ?: ResultCode.from(ex.statusCode)
        return ResponseEntity.status(status).body(
            failureBody(
                resultCode = resultCode,
                request = request,
                status = status.value(),
                message = ex.message ?: resultCode.message
            )
        )
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailure(
        @Suppress("UNUSED_PARAMETER") ex: OptimisticLockingFailureException,
        request: HttpServletRequest
    ): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ResultCode.CONFLICT.status).body(
            failureBody(
                resultCode = ResultCode.CONFLICT,
                request = request,
                message = "order was modified concurrently. retry the request"
            )
        )

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiResult.Failure> {
        val status = ResultCode.INTERNAL_ERROR.status
        log.error("Unexpected exception occurred. path={}", request.requestURI, ex)
        return ResponseEntity.status(status).body(
            failureBody(
                resultCode = ResultCode.from(status),
                request = request,
                status = status.value(),
                message = ResultCode.INTERNAL_ERROR.message
            )
        )
    }

    private fun failureBody(
        resultCode: ResultCode,
        request: HttpServletRequest,
        status: Int = resultCode.status.value(),
        message: String = resultCode.message,
        errors: Any? = null
    ): ApiResult.Failure =
        ApiResult.failure(
            resultCode = resultCode,
            status = status,
            message = message,
            path = request.requestURI,
            traceId = resolveTraceId(request),
            errors = errors
        )

    private fun resolveTraceId(request: HttpServletRequest): String? =
        request.getHeader(TRACE_ID_HEADER)
            ?: request.getAttribute(TRACE_ID_ATTRIBUTE)?.toString()

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
        private const val TRACE_ID_HEADER = "X-Trace-Id"
        private const val TRACE_ID_ATTRIBUTE = "traceId"
    }
}
