package com.example.kotlinspringbootsample.presentation.common

import org.springframework.http.HttpStatus

enum class ResultCode(
    val status: HttpStatus,
    val code: String,
    val message: String
) {
    SUCCESS(HttpStatus.OK, "200", "Success"),
    CREATED(HttpStatus.CREATED, "201", "Created"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "400", "Invalid Request"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "404", "Not Found"),
    CONFLICT(HttpStatus.CONFLICT, "409", "Conflict"),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "user already exists"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", "Idempotency-Key header has invalid format"),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "idempotency key conflict"),
    PAYMENT_APPROVAL_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_APPROVAL_FAILED", "payment approval failed"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "payment not found"),
    ILLEGAL_PAYMENT_STATE_TRANSITION(HttpStatus.CONFLICT, "ILLEGAL_PAYMENT_STATE_TRANSITION", "illegal payment state transition"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500", "Internal Server Error")

    ;

    companion object {
        fun from(status: HttpStatus): ResultCode = from(status.value())

        fun from(statusCode: Int): ResultCode =
            when (statusCode) {
                400 -> INVALID_REQUEST
                404 -> NOT_FOUND
                409 -> CONFLICT
                500 -> INTERNAL_ERROR
                else -> INTERNAL_ERROR
            }
    }
}
