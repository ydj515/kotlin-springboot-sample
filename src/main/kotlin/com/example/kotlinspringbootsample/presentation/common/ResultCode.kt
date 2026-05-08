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
    ALREADY_EXISTS(HttpStatus.CONFLICT, "409", "Already Exists"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500", "Internal Server Error")
}
