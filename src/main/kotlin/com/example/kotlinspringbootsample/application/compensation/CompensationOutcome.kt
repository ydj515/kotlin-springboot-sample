package com.example.kotlinspringbootsample.application.compensation

import java.time.LocalDateTime

/**
 * compensateApprovedPayment 결과.
 * - REFUNDED: PG.refund 즉시 성공, Payment.REFUNDED로 commit
 * - SCHEDULED: PG.refund 실패, CompensationTask 등록, Payment.REFUND_FAILED로 commit
 */
sealed class CompensationOutcome {
    data class Refunded(val refundedAt: LocalDateTime) : CompensationOutcome()
    data class Scheduled(val taskId: Long) : CompensationOutcome()
}
