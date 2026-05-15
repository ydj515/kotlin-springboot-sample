package com.example.kotlinspringbootsample.domain.compensation

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

@Entity
@Table(name = "compensation_tasks")
class CompensationTask(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 50)
    var taskType: CompensationTaskType,

    @Lob
    @Column(nullable = false)
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: CompensationTaskStatus = CompensationTaskStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_error", length = 1000)
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun markSuccess(now: LocalDateTime = LocalDateTime.now()) {
        status = CompensationTaskStatus.SUCCESS
        lastError = null
        updatedAt = now
    }

    fun markRetry(error: String, nextAttempt: LocalDateTime, now: LocalDateTime = LocalDateTime.now()) {
        retryCount += 1
        nextAttemptAt = nextAttempt
        lastError = error.take(LAST_ERROR_MAX_LENGTH)
        updatedAt = now
    }

    fun markFailed(error: String, now: LocalDateTime = LocalDateTime.now()) {
        status = CompensationTaskStatus.FAILED
        lastError = error.take(LAST_ERROR_MAX_LENGTH)
        updatedAt = now
    }

    companion object {
        private const val LAST_ERROR_MAX_LENGTH = 1000

        fun pending(
            taskType: CompensationTaskType,
            payload: String,
            now: LocalDateTime = LocalDateTime.now()
        ): CompensationTask = CompensationTask(
            taskType = taskType,
            payload = payload,
            nextAttemptAt = now,
            createdAt = now,
            updatedAt = now
        )
    }
}
