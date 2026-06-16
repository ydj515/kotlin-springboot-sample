package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.domain.payment.PaymentCompletionTaskStatus
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentCompletionTaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PaymentCompletionTaskService(
    private val paymentCompletionTaskRepository: PaymentCompletionTaskRepository
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claimPendingTasks(now: LocalDateTime, limit: Int, leaseUntil: LocalDateTime): List<Long> {
        val batch = paymentCompletionTaskRepository.findPendingForUpdate(now, limit)
        if (batch.isEmpty()) {
            return emptyList()
        }

        batch.forEach { it.nextAttemptAt = leaseUntil }
        paymentCompletionTaskRepository.saveAll(batch)
        return batch.mapNotNull { it.id }
    }

    @Transactional(readOnly = true)
    fun loadTaskSnapshot(taskId: Long): PaymentCompletionTaskSnapshot {
        val task = paymentCompletionTaskRepository.findById(taskId).orElseThrow {
            IllegalStateException("payment completion task not found: id=$taskId")
        }
        return PaymentCompletionTaskSnapshot(
            id = requireNotNull(task.id),
            orderId = task.orderId,
            paymentId = task.paymentId,
            paymentKey = task.paymentKey,
            amount = task.amount,
            approvedAt = task.approvedAt,
            status = task.status,
            retryCount = task.retryCount
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordSuccess(taskId: Long, now: LocalDateTime) {
        val task = paymentCompletionTaskRepository.findById(taskId).orElseThrow {
            IllegalStateException("payment completion task not found: id=$taskId")
        }
        task.markSuccess(now)
        paymentCompletionTaskRepository.save(task)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(
        taskId: Long,
        errorMessage: String,
        nextAttempt: LocalDateTime?,
        now: LocalDateTime
    ) {
        val task = paymentCompletionTaskRepository.findById(taskId).orElseThrow {
            IllegalStateException("payment completion task not found: id=$taskId")
        }

        if (nextAttempt == null) {
            task.markFailed("max retry exceeded: $errorMessage", now)
        } else {
            task.markRetry(errorMessage, nextAttempt, now)
        }
        paymentCompletionTaskRepository.save(task)
    }
}

data class PaymentCompletionTaskSnapshot(
    val id: Long,
    val orderId: Long,
    val paymentId: Long,
    val paymentKey: String,
    val amount: BigDecimal,
    val approvedAt: LocalDateTime,
    val status: PaymentCompletionTaskStatus,
    val retryCount: Int
)
