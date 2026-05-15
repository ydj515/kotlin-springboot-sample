package com.example.kotlinspringbootsample.infrastructure.compensation

import com.example.kotlinspringbootsample.application.compensation.CompensationService
import com.example.kotlinspringbootsample.domain.compensation.repository.CompensationTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * PENDING 보상 task를 polling하여 CompensationService에 위임.
 * - Outer lock 트랜잭션: SKIP LOCKED로 batch만 잠금
 * - Inner 처리 트랜잭션: REQUIRES_NEW (CompensationService.processCompensationTask)
 *   → outer 롤백이 inner 결과를 잃지 않게 분리
 */
@Component
class CompensationRetryWorker(
    private val compensationTaskRepository: CompensationTaskRepository,
    private val compensationService: CompensationService
) {

    @Scheduled(fixedDelayString = "\${app.compensation.worker.fixed-delay-ms:1000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun runBatch() {
        val now = LocalDateTime.now()
        val batch = compensationTaskRepository.findPendingForUpdate(now, BATCH_SIZE)
        if (batch.isEmpty()) return

        log.debug("compensation worker batch picked: size={}", batch.size)
        batch.forEach { task ->
            val taskId = task.id ?: return@forEach
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
    }
}
