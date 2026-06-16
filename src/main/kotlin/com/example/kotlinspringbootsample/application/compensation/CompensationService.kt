package com.example.kotlinspringbootsample.application.compensation

import com.example.kotlinspringbootsample.domain.compensation.CompensationTaskStatus
import com.example.kotlinspringbootsample.domain.compensation.CompensationTaskType
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class CompensationService(
    private val paymentGateway: PaymentGateway,
    private val compensationTransactionService: CompensationTransactionService,
    private val objectMapper: ObjectMapper
) {

    /**
     * PG 환불은 DB 트랜잭션 밖에서 수행한다.
     * 환불 결과 기록과 재시도 task 저장만 별도의 짧은 트랜잭션으로 처리한다.
     */
    fun compensateApprovedPayment(
        paymentId: Long,
        paymentKey: String,
        amount: BigDecimal,
        reason: String,
        now: LocalDateTime = LocalDateTime.now()
    ): CompensationOutcome {
        if (compensationTransactionService.isPaymentRefunded(paymentId)) {
            log.info("compensation skipped because payment is already refunded: paymentId={}", paymentId)
            return CompensationOutcome.Refunded(now)
        }

        return try {
            val result = paymentGateway.refund(paymentKey, amount)
            compensationTransactionService.recordRefundSucceeded(paymentId, result.refundedAt, reason)
            log.info(
                "compensation refund succeeded: paymentId={} reason={}",
                paymentId, reason
            )
            CompensationOutcome.Refunded(result.refundedAt)
        } catch (e: Exception) {
            val taskId = compensationTransactionService.recordRefundFailedAndSchedule(
                paymentId = paymentId,
                paymentKey = paymentKey,
                amount = amount,
                reason = reason,
                errorMessage = e.message ?: e.javaClass.simpleName,
                now = now
            )
            log.warn(
                "compensation refund failed, task scheduled: paymentId={} taskId={} error={}",
                paymentId, taskId, e.message
            )
            CompensationOutcome.Scheduled(taskId)
        }
    }

    /**
     * CompensationRetryWorker가 PENDING task를 위해 호출.
     * task claim은 worker에서 먼저 짧은 트랜잭션으로 끝내고, PG 환불은 여기서 트랜잭션 없이 수행한다.
     */
    fun processCompensationTask(taskId: Long, now: LocalDateTime = LocalDateTime.now()) {
        val task = compensationTransactionService.loadTaskSnapshot(taskId)
        if (task.status != CompensationTaskStatus.PENDING) {
            return
        }

        when (task.taskType) {
            CompensationTaskType.PG_REFUND -> retryPgRefund(task, now)
        }
    }

    private fun retryPgRefund(task: CompensationTaskSnapshot, now: LocalDateTime) {
        val payload = objectMapper.readValue(task.payload, PgRefundPayload::class.java)

        if (compensationTransactionService.isPaymentRefunded(payload.paymentId)) {
            compensationTransactionService.recordTaskRefundSucceeded(
                taskId = task.id,
                paymentId = payload.paymentId,
                refundedAt = now,
                now = now
            )
            log.info("compensation task short-circuit (already refunded): taskId={}", task.id)
            return
        }

        try {
            val result = paymentGateway.refund(payload.paymentKey, payload.amount)
            compensationTransactionService.recordTaskRefundSucceeded(
                taskId = task.id,
                paymentId = payload.paymentId,
                refundedAt = result.refundedAt,
                now = now
            )
            log.info("compensation task succeeded: taskId={}", task.id)
        } catch (e: Exception) {
            val nextRetry = task.retryCount + 1
            if (nextRetry >= MAX_RETRY) {
                compensationTransactionService.recordTaskRefundFailure(
                    taskId = task.id,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                    nextAttempt = null,
                    now = now
                )
                log.error(
                    "compensation task FAILED after max retry: taskId={} paymentId={} error={}",
                    task.id, payload.paymentId, e.message
                )
            } else {
                val backoffSeconds = backoffSeconds(nextRetry)
                compensationTransactionService.recordTaskRefundFailure(
                    taskId = task.id,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                    nextAttempt = now.plusSeconds(backoffSeconds),
                    now = now
                )
                log.warn(
                    "compensation task retry scheduled: taskId={} retryCount={} nextAttemptInSec={} error={}",
                    task.id, nextRetry, backoffSeconds, e.message
                )
            }
        }
    }

    private fun backoffSeconds(retryCount: Int): Long {
        val seconds = 1L shl retryCount  // 2^retryCount
        return seconds.coerceAtMost(MAX_BACKOFF_SECONDS)
    }

    data class PgRefundPayload(
        val paymentId: Long = 0,
        val paymentKey: String = "",
        val amount: BigDecimal = BigDecimal.ZERO,
        val reason: String = ""
    )

    companion object {
        const val MAX_RETRY = 3
        const val MAX_BACKOFF_SECONDS = 60L
        private val log = LoggerFactory.getLogger(CompensationService::class.java)
    }
}
