package com.example.kotlinspringbootsample.domain.payment.repository

import com.example.kotlinspringbootsample.domain.payment.PaymentCompletionTask
import com.example.kotlinspringbootsample.domain.payment.PaymentCompletionTaskStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PaymentCompletionTaskRepository : JpaRepository<PaymentCompletionTask, Long> {
    fun findFirstByPaymentIdAndStatus(
        paymentId: Long,
        status: PaymentCompletionTaskStatus
    ): PaymentCompletionTask?

    @Query(
        value = """
            SELECT * FROM payment_completion_tasks
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
            ORDER BY id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findPendingForUpdate(
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<PaymentCompletionTask>
}
