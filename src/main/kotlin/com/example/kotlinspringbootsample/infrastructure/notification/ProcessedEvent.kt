package com.example.kotlinspringbootsample.infrastructure.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId::class)
class ProcessedEvent(
    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    var eventId: String,

    @Id
    @Column(name = "consumer_name", nullable = false, length = 100)
    var consumerName: String,

    @Column(name = "processed_at", nullable = false)
    var processedAt: LocalDateTime = LocalDateTime.now()
)

data class ProcessedEventId(
    val eventId: String = "",
    val consumerName: String = ""
) : Serializable
