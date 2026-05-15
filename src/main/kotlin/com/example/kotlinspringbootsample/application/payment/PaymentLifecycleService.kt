package com.example.kotlinspringbootsample.application.payment

import com.example.kotlinspringbootsample.domain.payment.Payment
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentNotFoundException
import com.example.kotlinspringbootsample.domain.payment.repository.PaymentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Payment의 라이프사이클(REQUESTED / APPROVED / FAILED 전이)을 메인 트랜잭션과
 * 분리된 REQUIRES_NEW 트랜잭션으로 commit하는 서비스.
 *
 * 도입 이유 (Stripe PaymentIntent 패턴):
 * - payOrder 메인 트랜잭션이 보상 호출(REQUIRES_NEW) 시점에 아직 commit되지 않아
 *   해당 Payment row가 보이지 않는 격리 문제를 해소
 * - PG와 통신한 audit(승인/실패) 자체가 메인 흐름 실패와 무관하게 영구 보존
 * - 보상 흐름이 commit된 Payment.APPROVED를 정확히 REFUNDED/REFUND_FAILED로 전이 가능
 */
@Service
class PaymentLifecycleService(
    private val paymentRepository: PaymentRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createRequested(orderId: Long, idempotencyKey: String, amount: BigDecimal): Payment =
        paymentRepository.save(
            Payment.request(orderId = orderId, idempotencyKey = idempotencyKey, amount = amount)
        )

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markApproved(paymentId: Long, paymentKey: String, approvedAt: LocalDateTime): Payment {
        val payment = paymentRepository.findById(paymentId).orElseThrow {
            PaymentNotFoundException("payment not found: id=$paymentId")
        }
        payment.markApproved(paymentKey = paymentKey, approvedAt = approvedAt)
        return paymentRepository.save(payment)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(paymentId: Long, reason: String, occurredAt: LocalDateTime = LocalDateTime.now()) {
        val payment = paymentRepository.findById(paymentId).orElseThrow {
            PaymentNotFoundException("payment not found: id=$paymentId")
        }
        payment.markFailed(reason = reason, occurredAt = occurredAt)
        paymentRepository.save(payment)
    }
}
