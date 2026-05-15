package com.example.kotlinspringbootsample.infrastructure.notification

import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, ProcessedEventId> {
    fun existsByEventIdAndConsumerName(eventId: String, consumerName: String): Boolean
}
