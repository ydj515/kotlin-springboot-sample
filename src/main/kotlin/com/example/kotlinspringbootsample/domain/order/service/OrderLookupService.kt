package com.example.kotlinspringbootsample.domain.order.service

import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.exception.OrderNotFoundException
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import org.springframework.stereotype.Component

@Component
class OrderLookupService(
    private val orderRepository: OrderRepository
) {
    fun requireById(id: Long): Order =
        orderRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw OrderNotFoundException("Order not found with id $id")
}
