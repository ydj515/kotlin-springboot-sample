package com.example.kotlinspringbootsample.domain.notification

interface SmsSender {
    fun send(to: String, message: String)
}
