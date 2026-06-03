package com.example.kotlinspringbootsample.infrastructure.compensation

import com.example.kotlinspringbootsample.application.compensation.CompensationTransactionService
import com.example.kotlinspringbootsample.application.compensation.CompensationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * PENDING 보상 task를 polling하여 CompensationService에 위임.
 * - claim 트랜잭션: SKIP LOCKED로 batch를 짧게 잠그고 nextAttemptAt을 lease 시각으로 민다.
 * - PG refund 재시도: claim 트랜잭션이 끝난 뒤 수행한다.
 * - 결과 기록: CompensationService 내부의 짧은 트랜잭션에 위임한다.
 */
@Component
class CompensationRetryWorker(
    private val compensationTransactionService: CompensationTransactionService,
    private val compensationService: CompensationService
) {

    @Scheduled(fixedDelayString = "\${app.compensation.worker.fixed-delay-ms:1000}")
    fun runBatch() {
        val now = LocalDateTime.now()
        val taskIds = compensationTransactionService.claimPendingTasks(
            now = now,
            limit = BATCH_SIZE,
            leaseUntil = now.plusSeconds(CLAIM_LEASE_SECONDS)
        )
        if (taskIds.isEmpty()) return

        log.debug("compensation worker batch picked: size={}", taskIds.size)
        taskIds.forEach { taskId ->
            try {
                compensationService.processCompensationTask(taskId, now)
            } catch (e: Exception) {
                log.error("compensation task processing crashed: taskId={} error={}", taskId, e.message, e)
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(CompensationRetryWorker::class.java)
        const val BATCH_SIZE = 10
        const val CLAIM_LEASE_SECONDS = 300L
    }
}
