package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.compensation.CompensationService
import com.example.kotlinspringbootsample.application.order.command.CancelOrderCommand
import com.example.kotlinspringbootsample.application.order.command.CreateOrderCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrdersCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrderStatusSummariesCommand
import com.example.kotlinspringbootsample.application.order.command.GetOrderCommand
import com.example.kotlinspringbootsample.application.order.command.OrderSearchMode
import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.application.order.command.ShipOrderCommand
import com.example.kotlinspringbootsample.application.order.result.OrderResult
import com.example.kotlinspringbootsample.application.order.result.OrderStatusSummaryResult
import com.example.kotlinspringbootsample.application.order.result.OrderSummaryResult
import com.example.kotlinspringbootsample.application.order.result.PayOrderResult
import com.example.kotlinspringbootsample.domain.customer.service.CustomerLookupService
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.order.policy.OrderItemPolicy
import com.example.kotlinspringbootsample.domain.order.policy.OrderStatusTransitionPolicy
import com.example.kotlinspringbootsample.domain.order.service.OrderLookupService
import com.example.kotlinspringbootsample.domain.payment.exception.PaymentApprovalFailedException
import com.example.kotlinspringbootsample.domain.payment.gateway.PaymentGateway
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class OrderUseCase(
    private val orderRepository: OrderRepository,
    private val customerLookupService: CustomerLookupService,
    private val orderLookupService: OrderLookupService,
    private val orderItemPolicy: OrderItemPolicy,
    private val orderStatusTransitionPolicy: OrderStatusTransitionPolicy,
    private val paymentGateway: PaymentGateway,
    private val compensationService: CompensationService,
    private val orderPaymentTransactionService: OrderPaymentTransactionService
) {
    @Transactional(readOnly = true)
    fun getOrders(command: FindOrdersCommand): Page<OrderSummaryResult> {
        val pageable = PageRequest.of(command.page, command.size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val customerName = command.customerName?.takeIf(String::isNotBlank)
        val orders = when (command.searchMode) {
            OrderSearchMode.DERIVED -> findOrdersByDerivedQuery(customerName, command.status, pageable)
            OrderSearchMode.JPQL -> orderRepository.searchByConditions(customerName, command.status, pageable)
        }

        return orders.map { it.toSummaryResult() }
    }

    @Transactional(readOnly = true)
    fun getOrder(command: GetOrderCommand): OrderResult =
        orderLookupService.requireById(command.id).toResult()

    @Transactional(readOnly = true)
    fun getOrderStatusSummaries(command: FindOrderStatusSummariesCommand): List<OrderStatusSummaryResult> =
        orderRepository.findStatusSummaries(
            customerName = command.customerName?.takeIf(String::isNotBlank),
            status = command.status
        )
            .map { it.toResult() }

    @Transactional
    fun createOrder(command: CreateOrderCommand): OrderResult {
        val customer = customerLookupService.requireById(command.customerId)

        val drafts: List<OrderLineDraft> = command.toDrafts()
        orderItemPolicy.validate(drafts)

        return Order(
            customer = customer,
            orderNo = generateOrderNo(),
            shippingAddress = command.toShippingAddress(),
            orderedAt = LocalDateTime.now(),
            deliveryRequestedAt = command.deliveryRequestedAt
        )
            .apply { replaceLines(drafts) }
            .let(orderRepository::save)
            .toResult()
    }

    /**
     * 주문 결제를 처리한다.
     *
     * ## 흐름
     * 1. Payment.REQUESTED 저장 (TX-1, [OrderPaymentTransactionService.preparePayOrder])
     * 2. PG 승인 요청 (트랜잭션 외부 — DB 커넥션/락 미점유)
     * 3. Payment.APPROVED 기록 (TX-2, [OrderPaymentTransactionService.markPaymentApproved])
     * 4. Order.PAID 전이 + OrderPaidEvent outbox 저장 (TX-3, [OrderPaymentTransactionService.completePayOrder])
     *
     * 각 트랜잭션은 독립적으로 커밋되어, 외부 PG 호출 중 DB 락 전파를 방지하고
     * 부분 실패 시에도 이미 확정된 audit(Payment.APPROVED, paymentKey)을 잃지 않는다.
     *
     * ## 실패 전략
     * - **PG 승인 거절**: Payment.FAILED 기록 후 [PaymentApprovalFailedException] 전파
     * - **PG 승인 audit 저장 실패**: paymentKey를 알고 있으므로 즉시 환불 보상
     * - **주문 완료 실패 (복구 가능)**: [OrderStatus.PAYMENT_COMPLETION_PENDING] +
     *   [PaymentCompletionTask]로 durable retry, 고객에게 "처리 중" 응답
     * - **주문 완료 실패 (복구 불가)**: 즉시 환불 보상, 고객에게 "취소 진행 중" 응답
     * - **pending 기록 자체 실패**: 추적 가능한 복구 상태를 만들 수 없으므로 환불 보상으로 안전하게 닫음
     *
     * ## 응답 타입
     * | 상황 | [PayOrderResult] |
     * |------|------------------|
     * | 정상 완료 | [PayOrderResult.paid] |
     * | 복구 가능한 실패, 재시도 예정 | [PayOrderResult.processing] |
     * | 복구 불가, 환불 진행 중 | [PayOrderResult.canceling] |
     *
     * ## 멱등성
     * 동일 [PayOrderCommand.idempotencyKey]로 재요청 시 기존 Payment 상태에 따라
     * 적절한 replay 응답을 반환한다 ([PayOrderPreparation.Replay]).
     *
     * @param command 주문 ID와 멱등성 키를 포함하는 결제 명령
     * @return 결제 처리 결과 (paid / processing / canceling)
     * @throws PaymentApprovalFailedException PG가 승인을 명시적으로 거절한 경우
     * @see OrderPaymentTransactionService
     * @see CompensationService.compensateApprovedPayment
     */
    fun payOrder(command: PayOrderCommand): PayOrderResult {
        // 1. 주문 결제에 필요한 정보 조회 및 Payment.REQUESTED 저장.
        //    이 트랜잭션은 여기서 끝나야 PG approve 동안 DB 커넥션/락을 점유하지 않는다.
        val preparation = orderPaymentTransactionService.preparePayOrder(command)
        if (preparation is PayOrderPreparation.Replay) {
            return preparation.result
        }

        preparation as PayOrderPreparation.ApprovalRequired

        // 2. PG 승인 요청.
        //    실제 운영에서는 외부 HTTP 호출이므로 어떤 DB 트랜잭션에도 묶지 않는다.
        val approveResult = try {
            paymentGateway.approve(preparation.amount, preparation.idempotencyKey)
        } catch (e: PaymentApprovalFailedException) {
            // 3-A. PG가 명시적으로 승인을 거절한 경우 Payment.FAILED audit만 별도 기록한다.
            orderPaymentTransactionService.markPaymentFailed(
                paymentId = preparation.paymentId,
                reason = e.message ?: "PG declined"
            )
            throw e
        }

        try {
            // 3-B. PG 승인 성공 audit을 먼저 독립 트랜잭션으로 확정한다.
            //      completePayOrder와 한 트랜잭션으로 묶으면 주문/Outbox 저장 실패 시 Payment.APPROVED까지
            //      롤백되어 승인된 외부 결제의 paymentKey/audit을 잃고 환불 보상도 불안정해진다.
            orderPaymentTransactionService.markPaymentApproved(
                paymentId = preparation.paymentId,
                paymentKey = approveResult.paymentKey,
                approvedAt = approveResult.approvedAt
            )
        } catch (e: Exception) {
            // 3-C. PG는 승인했지만 승인 audit 저장이 실패한 경우 즉시 환불 보상을 시도한다.
            compensationService.compensateApprovedPayment(
                paymentId = preparation.paymentId,
                paymentKey = approveResult.paymentKey,
                amount = preparation.amount,
                reason = "payOrder approval persistence failure: ${e.message}"
            )
            throw e
        }

        return try {
            // 4. 주문 PAID 전이와 OrderPaidEvent outbox 저장은 하나의 짧은 트랜잭션으로 묶는다.
            //    여기서 성공하면 결제 완료를 즉시 응답한다.
            PayOrderResult.paid(
                orderPaymentTransactionService.completePayOrder(
                    orderId = preparation.orderId,
                    paymentId = preparation.paymentId,
                    paymentKey = approveResult.paymentKey,
                    approvedAt = approveResult.approvedAt
                )
            )
        } catch (e: NonRecoverablePaymentCompletionException) {
            // 5-A. 주문 상태/결제 상태가 복구 불가능하게 어긋났다면 고객에게 성공으로 말하지 않고 환불 보상으로 닫는다.
            compensationService.compensateApprovedPayment(
                paymentId = preparation.paymentId,
                paymentKey = approveResult.paymentKey,
                amount = preparation.amount,
                reason = "payOrder non-recoverable downstream failure: ${e.message}"
            )
            PayOrderResult.canceling(
                orderPaymentTransactionService.getOrderResult(
                    orderId = preparation.orderId,
                    paymentKey = approveResult.paymentKey
                )
            )
        } catch (e: Exception) {
            // 5-B. DB deadlock, timeout, outbox insert 실패처럼 복구 가능한 실패는 pending 상태와 durable retry task로 남긴다.
            val pendingOrder = try {
                orderPaymentTransactionService.markPaymentCompletionPendingAndSchedule(
                    orderId = preparation.orderId,
                    paymentId = preparation.paymentId,
                    paymentKey = approveResult.paymentKey,
                    amount = preparation.amount,
                    approvedAt = approveResult.approvedAt,
                    reason = "payOrder completion failure: ${e.message}"
                )
            } catch (pendingFailure: Exception) {
                // pending 기록 자체가 실패하면 추적 가능한 복구 상태를 만들 수 없으므로 환불 보상으로 안전하게 닫는다.
                compensationService.compensateApprovedPayment(
                    paymentId = preparation.paymentId,
                    paymentKey = approveResult.paymentKey,
                    amount = preparation.amount,
                    reason = "payOrder pending persistence failure: ${pendingFailure.message}"
                )
                return PayOrderResult.canceling(
                    orderPaymentTransactionService.getOrderResult(
                        orderId = preparation.orderId,
                        paymentKey = approveResult.paymentKey
                    )
                )
            }

            PayOrderResult.processing(pendingOrder)
        }
    }

    @Transactional
    fun shipOrder(command: ShipOrderCommand): OrderResult =
        orderLookupService.requireById(command.id)
            .apply {
                orderStatusTransitionPolicy.validateShippable(this)
                markShipped()
            }
            .toResult()

    fun cancelOrder(command: CancelOrderCommand): OrderResult {
        return when (val preparation = orderPaymentTransactionService.prepareCancelOrder(command)) {
            is CancelOrderPreparation.Replay -> preparation.result
            is CancelOrderPreparation.Completed -> preparation.result
            is CancelOrderPreparation.RefundRequired -> {
                val outcome = compensationService.compensateApprovedPayment(
                    paymentId = preparation.paymentId,
                    paymentKey = preparation.paymentKey,
                    amount = preparation.amount,
                    reason = preparation.reason
                )
                orderPaymentTransactionService.recordCancellationRefundOutcome(preparation.cancellationId, outcome)
                preparation.result
            }
        }
    }

    private fun findOrdersByDerivedQuery(
        customerName: String?,
        status: OrderStatus?,
        pageable: PageRequest
    ): Page<Order> =
        when {
            customerName != null && status != null ->
                orderRepository.findAllByCustomerNameAndStatusAndDeletedAtIsNull(customerName, status, pageable)

            customerName != null ->
                orderRepository.findAllByCustomerNameAndDeletedAtIsNull(customerName, pageable)

            status != null ->
                orderRepository.findAllByDeletedAtIsNullAndStatus(status, pageable)

            else ->
                orderRepository.findAllByDeletedAtIsNull(pageable)
        }

    private fun generateOrderNo(): String {
        val datePart = LocalDate.now().format(DATE_FORMATTER)
        val randomPart = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        return "ORD-$datePart-$randomPart"
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
