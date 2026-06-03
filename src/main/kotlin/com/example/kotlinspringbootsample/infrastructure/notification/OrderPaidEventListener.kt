package com.example.kotlinspringbootsample.infrastructure.notification

import com.example.kotlinspringbootsample.domain.notification.SmsSender
import com.example.kotlinspringbootsample.domain.order.event.OrderPaidEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderPaidEventListener(
    private val processedEventService: ProcessedEventService,
    private val smsSender: SmsSender
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: OrderPaidEvent) {
        if (!processedEventService.tryMarkProcessed(event.eventId, CONSUMER_NAME)) {
            return
        }

        smsSender.send(
            to = "buyer-of-order-${event.orderId}",
            message = "주문 ${event.orderId} 결제가 완료되었습니다. (paymentKey=${event.paymentKey})"
        )
    }

    private companion object {
        const val CONSUMER_NAME = "order-paid-sms-sender"
    }
}
