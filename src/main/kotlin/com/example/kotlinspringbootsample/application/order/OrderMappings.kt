package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.order.command.CreateOrderCommand
import com.example.kotlinspringbootsample.application.order.result.OrderLineResult
import com.example.kotlinspringbootsample.application.order.result.OrderResult
import com.example.kotlinspringbootsample.application.order.result.OrderStatusSummaryResult
import com.example.kotlinspringbootsample.application.order.result.OrderSummaryResult
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.ShippingAddress
import com.example.kotlinspringbootsample.domain.order.repository.projection.OrderStatusSummaryProjection

internal fun CreateOrderCommand.toShippingAddress(): ShippingAddress =
    ShippingAddress(
        recipient = recipient,
        zipCode = zipCode,
        address1 = address1,
        address2 = address2
    )

internal fun CreateOrderCommand.toDrafts(): List<OrderLineDraft> =
    items.map {
        OrderLineDraft(
            productName = it.productName,
            quantity = it.quantity,
            unitPrice = it.unitPrice
        )
    }

internal fun Order.toSummaryResult(): OrderSummaryResult =
    OrderSummaryResult(
        id = requireNotNull(id),
        version = version ?: 0L,
        buyerUsername = buyer.username,
        status = status,
        totalAmount = totalAmount,
        paidAt = paidAt,
        shippedAt = shippedAt,
        cancelledAt = cancelledAt,
        createdAt = createdAt
    )

internal fun Order.toResult(): OrderResult =
    OrderResult(
        id = requireNotNull(id),
        version = version ?: 0L,
        buyerUsername = buyer.username,
        status = status,
        recipient = shippingAddress.recipient,
        zipCode = shippingAddress.zipCode,
        address1 = shippingAddress.address1,
        address2 = shippingAddress.address2,
        totalAmount = totalAmount,
        items = lines.map { line ->
            OrderLineResult(
                productName = line.productName,
                quantity = line.quantity,
                unitPrice = line.unitPrice,
                lineAmount = line.totalPrice()
            )
        },
        paidAt = paidAt,
        shippedAt = shippedAt,
        cancelledAt = cancelledAt,
        createdAt = createdAt
    )

internal fun OrderStatusSummaryProjection.toResult(): OrderStatusSummaryResult =
    OrderStatusSummaryResult(
        status = status,
        count = count
    )
