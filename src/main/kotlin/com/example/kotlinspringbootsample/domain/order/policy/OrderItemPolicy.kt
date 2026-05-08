package com.example.kotlinspringbootsample.domain.order.policy

import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.exception.InvalidOrderItemException
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class OrderItemPolicy {
    fun validate(items: List<OrderLineDraft>) {
        if (items.isEmpty()) {
            throw InvalidOrderItemException("order items must not be empty")
        }

        items.forEach { item ->
            if (item.productName.isBlank()) {
                throw InvalidOrderItemException("product name must not be blank")
            }
            if (item.quantity <= 0) {
                throw InvalidOrderItemException("quantity must be greater than zero")
            }
            if (item.unitPrice <= BigDecimal.ZERO) {
                throw InvalidOrderItemException("unit price must be greater than zero")
            }
        }
    }
}
