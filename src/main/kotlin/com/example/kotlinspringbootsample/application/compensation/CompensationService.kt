package com.example.kotlinspringbootsample.application.compensation

import com.example.kotlinspringbootsample.domain.compensation.CompensationTask
import com.example.kotlinspringbootsample.domain.compensation.CompensationTaskType
import com.example.kotlinspringbootsample.domain.compensation.repository.CompensationTaskRepository
import com.example.kotlinspringbootsample.domain.payment.PaymentStatus
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentNotFoundException
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class CompensationService(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val compensationTaskRepository: CompensationTaskRepository,
    private val objectMapper: ObjectMapper
) {

    /**
     * payOrder 메인 트랜잭션이 PG.approve 성공 후 downstream에서 실패한 경우 호출.
     * REQUIRES_NEW로 메인 트랜잭션 롤백과 독립적으로 commit한다.
     *
     * - PG.refund 성공 → Payment.markRefunded + save (보상 완료)
     * - PG.refund 실패 → Payment.markRefundFailed + CompensationTask insert (worker가 재시도)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun compensateApprovedPayment(
        paymentId: Long,
        paymentKey: String,
        amount: BigDecimal,
        reason: String,
        now: LocalDateTime = LocalDateTime.now()
    ): CompensationOutcome {
        val payment = paymentRepository.findById(paymentId).orElseThrow {
            PaymentNotFoundException("payment not found: id=$paymentId")
        }

        return try {
            val result = paymentGateway.refund(paymentKey, amount)
            payment.markRefunded(result.refundedAt, "compensation refund: $reason")
            paymentRepository.save(payment)
            log.info("compensation refund succeeded: paymentId={} reason={}", paymentId, reason)
            CompensationOutcome.Refunded(result.refundedAt)
        } catch (e: Exception) {
            payment.markRefundFailed("compensation refund failed: ${e.message}", now)
            paymentRepository.save(payment)

            val payload = objectMapper.writeValueAsString(
                PgRefundPayload(
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
            log.warn(
                "compensation refund failed, task scheduled: paymentId={} taskId={} error={}",
                paymentId, task.id, e.message
            )
            CompensationOutcome.Scheduled(requireNotNull(task.id))
        }
    }

    /**
     * CompensationRetryWorker가 PENDING task를 위해 호출.
     * REQUIRES_NEW로 worker 루프의 row-lock 트랜잭션과 분리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processCompensationTask(taskId: Long, now: LocalDateTime = LocalDateTime.now()) {
        val task = compensationTaskRepository.findById(taskId).orElseThrow {
            IllegalStateException("compensation task not found: id=$taskId")
        }

        when (task.taskType) {
            CompensationTaskType.PG_REFUND -> retryPgRefund(task, now)
        }
    }

    private fun retryPgRefund(task: CompensationTask, now: LocalDateTime) {
        val payload = objectMapper.readValue(task.payload, PgRefundPayload::class.java)
        val payment = paymentRepository.findById(payload.paymentId).orElseThrow {
            PaymentNotFoundException("payment not found: id=${payload.paymentId}")
        }

        // 이미 다른 경로로 환불된 경우 (e.g. 동시 cancel 등) — task 즉시 완료 처리
        if (payment.status == PaymentStatus.REFUNDED) {
            task.markSuccess(now)
            compensationTaskRepository.save(task)
            log.info("compensation task short-circuit (already refunded): taskId={}", task.id)
            return
        }

        try {
            val result = paymentGateway.refund(payload.paymentKey, payload.amount)
            payment.markRefunded(result.refundedAt, "compensation retry succeeded")
            paymentRepository.save(payment)
            task.markSuccess(now)
            compensationTaskRepository.save(task)
            log.info("compensation task succeeded: taskId={}", task.id)
        } catch (e: Exception) {
            val nextRetry = task.retryCount + 1
            if (nextRetry >= MAX_RETRY) {
                task.markFailed("max retry exceeded: ${e.message}", now)
                compensationTaskRepository.save(task)
                log.error(
                    "compensation task FAILED after max retry: taskId={} paymentId={} error={}",
                    task.id, payload.paymentId, e.message
                )
            } else {
                val backoffSeconds = backoffSeconds(nextRetry)
                task.markRetry(e.message ?: "unknown", now.plusSeconds(backoffSeconds), now)
                compensationTaskRepository.save(task)
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
