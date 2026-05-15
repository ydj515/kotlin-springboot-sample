package com.example.kotlinspringbootsample.domain.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "outbox_events")
class OutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    var eventId: String,

    @Column(name = "aggregate_type", nullable = false, length = 50)
    var aggregateType: String,

    @Column(name = "aggregate_id", nullable = false, length = 100)
    var aggregateId: String,

    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String,

    @Lob
    @Column(nullable = false)
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "published_at")
    var publishedAt: LocalDateTime? = null,

    @Column(name = "last_error", length = 1000)
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun markPublished(publishedAt: LocalDateTime = LocalDateTime.now()) {
        status = OutboxStatus.PUBLISHED
        this.publishedAt = publishedAt
        lastError = null
    }

    fun markRetry(error: String, nextAttempt: LocalDateTime) {
        retryCount += 1
        nextAttemptAt = nextAttempt
        lastError = error.take(LAST_ERROR_MAX_LENGTH)
    }

    fun markFailed(error: String) {
        status = OutboxStatus.FAILED
        lastError = error.take(LAST_ERROR_MAX_LENGTH)
    }

    companion object {
        private const val LAST_ERROR_MAX_LENGTH = 1000

        fun pending(
            aggregateType: String,
            aggregateId: String,
            eventType: String,
            payload: String,
            now: LocalDateTime = LocalDateTime.now()
        ): OutboxEvent = OutboxEvent(
            eventId = UUID.randomUUID().toString(),
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            nextAttemptAt = now,
            createdAt = now
        )
    }
}
