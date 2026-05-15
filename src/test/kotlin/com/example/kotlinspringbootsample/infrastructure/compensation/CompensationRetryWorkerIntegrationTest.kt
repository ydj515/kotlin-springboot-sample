package com.example.kotlinspringbootsample.infrastructure.compensation

import com.example.kotlinspringbootsample.domain.compensation.CompensationTask
import com.example.kotlinspringbootsample.domain.compensation.CompensationTaskStatus
import com.example.kotlinspringbootsample.domain.compensation.CompensationTaskType
import com.example.kotlinspringbootsample.domain.compensation.repository.CompensationTaskRepository
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import com.example.kotlinspringbootsample.domain.payment.gateway.RefundResult
import com.example.kotlinspringbootsample.support.MySqlIntegrationTestSupport
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class CompensationRetryWorkerIntegrationTest @Autowired constructor(
    private val worker: CompensationRetryWorker,
    private val compensationTaskRepository: CompensationTaskRepository,
    private val objectMapper: ObjectMapper,
    private val jdbcTemplate: JdbcTemplate,
    @MockkBean private val paymentGateway: PaymentGateway
) : MySqlIntegrationTestSupport() {

    @AfterEach
    fun cleanup() {
        jdbcTemplate.update("DELETE FROM compensation_tasks")
    }

    @Test
    fun `worker가 PENDING task를 처리하면 PG refund 성공시 task SUCCESS로 전이된다`() {
        val taskId = insertPendingTask(paymentKey = "MOCK-PG-retry-1", amount = BigDecimal("1000.00"))

        every { paymentGateway.refund("MOCK-PG-retry-1", any()) } returns
            RefundResult(refundedAt = LocalDateTime.now().withNano(0))

        worker.runBatch()

        val task = compensationTaskRepository.findById(taskId).orElseThrow()
        assertThat(task.status).isEqualTo(CompensationTaskStatus.SUCCESS)
        assertThat(task.lastError).isNull()
    }

    @Test
    fun `worker가 PG refund를 3회 연속 실패하면 task가 FAILED로 종결된다 (max retry exceeded)`() {
        val taskId = insertPendingTask(paymentKey = "MOCK-PG-retry-fail", amount = BigDecimal("2000.00"))

        every { paymentGateway.refund("MOCK-PG-retry-fail", any()) } throws
            RuntimeException("persistent PG failure")

        // 1차 시도 — retry_count 1로 PENDING + nextAttemptAt 미래
        worker.runBatch()
        resetNextAttempt(taskId)

        // 2차 시도 — retry_count 2로 PENDING
        worker.runBatch()
        resetNextAttempt(taskId)

        // 3차 시도 — MAX_RETRY(3) 도달 → FAILED
        worker.runBatch()

        val task = compensationTaskRepository.findById(taskId).orElseThrow()
        assertThat(task.status).isEqualTo(CompensationTaskStatus.FAILED)
        assertThat(task.lastError).contains("max retry exceeded")
    }

    private fun insertPendingTask(paymentKey: String, amount: BigDecimal): Long {
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "paymentId" to 9999L,
                "paymentKey" to paymentKey,
                "amount" to amount,
                "reason" to "integration test"
            )
        )
        val saved = compensationTaskRepository.save(
            CompensationTask.pending(
                taskType = CompensationTaskType.PG_REFUND,
                payload = payload,
                now = LocalDateTime.now().minusSeconds(1)  // 즉시 worker 폴링 대상으로
            )
        )
        return saved.id!!
    }

    private fun resetNextAttempt(taskId: Long) {
        // 다음 worker 호출에서 즉시 polling되도록 next_attempt_at을 과거로 조정
        jdbcTemplate.update(
            "UPDATE compensation_tasks SET next_attempt_at = ? WHERE id = ?",
            LocalDateTime.now().minusSeconds(1), taskId
        )
    }
}
