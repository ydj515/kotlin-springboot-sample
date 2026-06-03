package com.example.kotlinspringbootsample.application.compensation

import com.example.kotlinspringbootsample.domain.compensation.CompensationTask
import com.example.kotlinspringbootsample.domain.compensation.CompensationTaskStatus
import com.example.kotlinspringbootsample.domain.compensation.CompensationTaskType
import com.example.kotlinspringbootsample.domain.compensation.repository.CompensationTaskRepository
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class CompensationTransactionService(
    private val paymentRepository: PaymentRepository,
    private val compensationTaskRepository: CompensationTaskRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordRefundSucceeded(paymentId: Long, refundedAt: LocalDateTime, reason: String) {
        val payment = paymentRepository.findById(paymentId).orElse(null) ?: return
        if (payment.status == PaymentStatus.APPROVED || payment.status == PaymentStatus.REFUND_FAILED) {
            payment.markRefunded(refundedAt, "compensation refund: $reason")
            paymentRepository.save(payment)
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordRefundFailedAndSchedule(
        paymentId: Long,
        paymentKey: String,
        amount: BigDecimal,
        reason: String,
        errorMessage: String,
        now: LocalDateTime
    ): Long {
        val payment = paymentRepository.findById(paymentId).orElse(null)
        if (payment != null && payment.status == PaymentStatus.APPROVED) {
            payment.markRefundFailed("compensation refund failed: $errorMessage", now)
            paymentRepository.save(payment)
        }

        val payload = objectMapper.writeValueAsString(
            CompensationService.PgRefundPayload(
                paymentId = paymentId,
                paymentKey = paymentKey,
                amount = amount,
                reason = reason
            )
        )
        val task = compensationTaskRepository.save(
            CompensationTask.pending(
                taskType = CompensationTaskType.PG_REFUND,
                payload = payload,
                now = now
            )
        )
        return requireNotNull(task.id)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claimPendingTasks(now: LocalDateTime, limit: Int, leaseUntil: LocalDateTime): List<Long> {
        val batch = compensationTaskRepository.findPendingForUpdate(now, limit)
        if (batch.isEmpty()) {
            return emptyList()
        }

        batch.forEach { it.nextAttemptAt = leaseUntil }
        compensationTaskRepository.saveAll(batch)
        return batch.mapNotNull { it.id }
    }

    @Transactional(readOnly = true)
    fun loadTaskSnapshot(taskId: Long): CompensationTaskSnapshot {
        val task = compensationTaskRepository.findById(taskId).orElseThrow {
            IllegalStateException("compensation task not found: id=$taskId")
        }
        return CompensationTaskSnapshot(
            id = requireNotNull(task.id),
            taskType = task.taskType,
            status = task.status,
            retryCount = task.retryCount,
            payload = task.payload
        )
    }

    @Transactional(readOnly = true)
    fun isPaymentRefunded(paymentId: Long): Boolean =
        paymentRepository.findById(paymentId).orElse(null)?.status == PaymentStatus.REFUNDED

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordTaskRefundSucceeded(
        taskId: Long,
        paymentId: Long,
        refundedAt: LocalDateTime,
        now: LocalDateTime
    ) {
        val task = compensationTaskRepository.findById(taskId).orElseThrow {
            IllegalStateException("compensation task not found: id=$taskId")
        }
        val payment = paymentRepository.findById(paymentId).orElse(null)
        if (payment != null && (payment.status == PaymentStatus.APPROVED || payment.status == PaymentStatus.REFUND_FAILED)) {
            payment.markRefunded(refundedAt, "compensation retry succeeded")
            paymentRepository.save(payment)
        }

        task.markSuccess(now)
        compensationTaskRepository.save(task)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordTaskRefundFailure(
        taskId: Long,
        errorMessage: String,
        nextAttempt: LocalDateTime?,
        now: LocalDateTime
    ): CompensationTaskSnapshot {
        val task = compensationTaskRepository.findById(taskId).orElseThrow {
            IllegalStateException("compensation task not found: id=$taskId")
        }

        if (nextAttempt == null) {
            task.markFailed("max retry exceeded: $errorMessage", now)
        } else {
            task.markRetry(errorMessage, nextAttempt, now)
        }
        compensationTaskRepository.save(task)

        return CompensationTaskSnapshot(
            id = requireNotNull(task.id),
            taskType = task.taskType,
            status = task.status,
            retryCount = task.retryCount,
            payload = task.payload
        )
    }
}

data class CompensationTaskSnapshot(
    val id: Long,
    val taskType: CompensationTaskType,
    val status: CompensationTaskStatus,
    val retryCount: Int,
    val payload: String
)
