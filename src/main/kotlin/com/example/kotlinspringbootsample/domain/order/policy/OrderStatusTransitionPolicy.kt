package com.example.kotlinspringbootsample.domain.order.policy

import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.exception.InvalidOrderStatusTransitionException
import org.springframework.stereotype.Component

@Component
class OrderStatusTransitionPolicy {
    fun validatePayable(order: Order) {
        if (order.status != OrderStatus.CREATED) {
            throw InvalidOrderStatusTransitionException(
                "only created orders can be paid. current status: ${order.status}"
            )
        }
    }

    fun validatePaymentCompletable(order: Order) {
        if (order.status !in setOf(OrderStatus.CREATED, OrderStatus.PAYMENT_COMPLETION_PENDING)) {
            throw InvalidOrderStatusTransitionException(
                "only created or payment completion pending orders can be completed. current status: ${order.status}"
            )
        }
    }

    fun validateShippable(order: Order) {
        if (order.status != OrderStatus.PAID) {
            throw InvalidOrderStatusTransitionException(
                "only paid orders can be shipped. current status: ${order.status}"
            )
        }
    }

    fun validateCancellable(order: Order) {
        if (order.status !in setOf(OrderStatus.CREATED, OrderStatus.PAID)) {
            throw InvalidOrderStatusTransitionException(
                "only created or paid orders can be cancelled. current status: ${order.status}"
            )
        }
    }
}
