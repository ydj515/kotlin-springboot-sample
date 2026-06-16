package com.example.kotlinspringbootsample.infrastructure.payment

import com.example.kotlinspringbootsample.application.compensation.CompensationService
import com.example.kotlinspringbootsample.application.order.NonRecoverablePaymentCompletionException
import com.example.kotlinspringbootsample.application.order.OrderPaymentTransactionService
import com.example.kotlinspringbootsample.application.order.PaymentCompletionTaskService
import com.example.kotlinspringbootsample.application.order.PaymentCompletionTaskSnapshot
import com.example.kotlinspringbootsample.domain.payment.PaymentCompletionTaskStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class PaymentCompletionRetryWorker(
    private val paymentCompletionTaskService: PaymentCompletionTaskService,
    private val orderPaymentTransactionService: OrderPaymentTransactionService,
    private val compensationService: CompensationService
) {
    @Scheduled(
        fixedDelayString = "\${app.payment-completion.worker.fixed-delay-ms:1000}",
        initialDelayString = "\${app.payment-completion.worker.initial-delay-ms:1000}"
    )
    fun runBatch() {
        val now = LocalDateTime.now()
        val taskIds = paymentCompletionTaskService.claimPendingTasks(
            now = now,
            limit = BATCH_SIZE,
            leaseUntil = now.plusSeconds(CLAIM_LEASE_SECONDS)
        )
        if (taskIds.isEmpty()) return

        log.debug("payment completion worker batch picked: size={}", taskIds.size)
        taskIds.forEach { taskId ->
            try {
                processTask(taskId, now)
            } catch (e: Exception) {
                log.error("payment completion task processing crashed: taskId={} error={}", taskId, e.message, e)
            }
        }
    }

    private fun processTask(taskId: Long, now: LocalDateTime) {
        val task = paymentCompletionTaskService.loadTaskSnapshot(taskId)
        if (task.status != PaymentCompletionTaskStatus.PENDING) {
            return
        }

        try {
            orderPaymentTransactionService.completePayOrder(
                orderId = task.orderId,
                paymentId = task.paymentId,
                paymentKey = task.paymentKey,
                approvedAt = task.approvedAt
            )
            paymentCompletionTaskService.recordSuccess(task.id, now)
            log.info(
                "payment completion task succeeded: taskId={} orderId={} paymentId={}",
                task.id, task.orderId, task.paymentId
            )
        } catch (e: Exception) {
            handleFailure(task, e, now)
        }
    }

    private fun handleFailure(task: PaymentCompletionTaskSnapshot, e: Exception, now: LocalDateTime) {
        val nextRetry = task.retryCount + 1
        val nonRecoverable = e is NonRecoverablePaymentCompletionException
        if (nonRecoverable || nextRetry >= MAX_RETRY) {
            try {
                compensationService.compensateApprovedPayment(
                    paymentId = task.paymentId,
                    paymentKey = task.paymentKey,
                    amount = task.amount,
                    reason = "payment completion retry exhausted: ${e.message}"
                )
                paymentCompletionTaskService.recordFailure(
                    taskId = task.id,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                    nextAttempt = null,
                    now = now
                )
                log.error(
                    "payment completion task failed, refund compensation requested: taskId={} orderId={} paymentId={} error={}",
                    task.id, task.orderId, task.paymentId, e.message
                )
            } catch (compensationError: Exception) {
                val backoffSeconds = backoffSeconds(nextRetry)
                paymentCompletionTaskService.recordFailure(
                    taskId = task.id,
                    errorMessage = compensationError.message ?: compensationError.javaClass.simpleName,
                    nextAttempt = now.plusSeconds(backoffSeconds),
                    now = now
                )
                log.error(
                    "payment completion refund compensation failed to persist, retry kept alive: taskId={} paymentId={} error={}",
                    task.id, task.paymentId, compensationError.message, compensationError
                )
            }
            return
        }

        val backoffSeconds = backoffSeconds(nextRetry)
        paymentCompletionTaskService.recordFailure(
            taskId = task.id,
            errorMessage = e.message ?: e.javaClass.simpleName,
            nextAttempt = now.plusSeconds(backoffSeconds),
            now = now
        )
        log.warn(
            "payment completion task retry scheduled: taskId={} retryCount={} nextAttemptInSec={} error={}",
            task.id, nextRetry, backoffSeconds, e.message
        )
    }

    private fun backoffSeconds(retryCount: Int): Long {
        val seconds = 1L shl retryCount
        return seconds.coerceAtMost(MAX_BACKOFF_SECONDS)
    }

    private companion object {
        val log = LoggerFactory.getLogger(PaymentCompletionRetryWorker::class.java)
        const val BATCH_SIZE = 10
        const val CLAIM_LEASE_SECONDS = 300L
        const val MAX_RETRY = 6
        const val MAX_BACKOFF_SECONDS = 60L
    }
}
