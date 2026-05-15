package com.example.kotlinspringbootsample.domain.outbox

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
