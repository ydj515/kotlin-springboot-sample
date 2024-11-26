package com.example.kotlinspringbootsample.common.dto

enum class ResultCode(val code: String, val message: String) {
    SUCCESS("200", "Success"),
    NOT_FOUND("404", "Not Found"),
    INTERNAL_ERROR("500", "Internal Server Error")
}