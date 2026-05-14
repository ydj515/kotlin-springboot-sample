package com.example.kotlinspringbootsample.application.order

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
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.policy.OrderItemPolicy
import com.example.kotlinspringbootsample.domain.order.policy.OrderStatusTransitionPolicy
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.order.service.OrderLookupService
import com.example.kotlinspringbootsample.domain.user.service.UserLookupService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class OrderUseCase(
    private val orderRepository: OrderRepository,
    private val userLookupService: UserLookupService,
    private val orderLookupService: OrderLookupService,
    private val orderItemPolicy: OrderItemPolicy,
    private val orderStatusTransitionPolicy: OrderStatusTransitionPolicy
) {
    fun getOrders(command: FindOrdersCommand): Page<OrderSummaryResult> {
        val pageable = PageRequest.of(command.page, command.size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val buyerUsername = command.buyerUsername?.takeIf(String::isNotBlank)
        val orders = when (command.searchMode) {
            OrderSearchMode.DERIVED -> findOrdersByDerivedQuery(buyerUsername, command.status, pageable)
            OrderSearchMode.JPQL -> orderRepository.searchByConditions(buyerUsername, command.status, pageable)
        }

        return orders.map { it.toSummaryResult() }
    }

    fun getOrder(command: GetOrderCommand): OrderResult =
        orderLookupService.requireById(command.id).toResult()

    fun getOrderStatusSummaries(command: FindOrderStatusSummariesCommand): List<OrderStatusSummaryResult> =
        orderRepository.findStatusSummaries(
            buyerUsername = command.buyerUsername?.takeIf(String::isNotBlank),
            status = command.status
        )
            .map { it.toResult() }

    @Transactional
    fun createOrder(command: CreateOrderCommand): OrderResult {
        val buyer = userLookupService.requireByUsername(command.buyerUsername)

        val drafts: List<OrderLineDraft> = command.toDrafts()
        orderItemPolicy.validate(drafts)

        return Order(
            buyer = buyer,
            shippingAddress = command.toShippingAddress()
        )
            .apply { replaceLines(drafts) }
            .let(orderRepository::save)
            .toResult()
    }

    @Transactional
    fun payOrder(command: PayOrderCommand): OrderResult =
        orderLookupService.requireById(command.id)
            .apply {
                orderStatusTransitionPolicy.validatePayable(this)
                markPaid()
            }
            .toResult()

    @Transactional
    fun shipOrder(command: ShipOrderCommand): OrderResult =
        orderLookupService.requireById(command.id)
            .apply {
                orderStatusTransitionPolicy.validateShippable(this)
                markShipped()
            }
            .toResult()

    @Transactional
    fun cancelOrder(command: CancelOrderCommand): OrderResult =
        orderLookupService.requireById(command.id)
            .apply {
                orderStatusTransitionPolicy.validateCancellable(this)
                cancel()
            }
            .toResult()

    private fun findOrdersByDerivedQuery(
        buyerUsername: String?,
        status: OrderStatus?,
        pageable: PageRequest
    ): Page<Order> =
        when {
            buyerUsername != null && status != null ->
                orderRepository.findAllByBuyerUsernameAndStatusAndDeletedAtIsNull(buyerUsername, status, pageable)

            buyerUsername != null ->
                orderRepository.findAllByBuyerUsernameAndDeletedAtIsNull(buyerUsername, pageable)

            status != null ->
                orderRepository.findAllByDeletedAtIsNullAndStatus(status, pageable)

            else ->
                orderRepository.findAllByDeletedAtIsNull(pageable)
        }
}
