package com.example.kotlinspringbootsample.infrastructure.notification

import com.example.kotlinspringbootsample.domain.notification.SmsSender
import com.example.kotlinspringbootsample.domain.order.event.OrderPaidEvent
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderPaidEventListener(
    private val processedEventRepository: ProcessedEventRepository,
    private val smsSender: SmsSender
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handle(event: OrderPaidEvent) {
        // 사전 dedup 체크 — 같은 (eventId, consumerName) 조합이 이미 처리됐으면 skip
        if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId, CONSUMER_NAME)) {
            log.info("event already processed: eventId={} consumer={}", event.eventId, CONSUMER_NAME)
            return
        }

        // race 보호: 두 인스턴스가 동시에 existsBy=false를 본 경우 한쪽은 UNIQUE 위반
        try {
            processedEventRepository.saveAndFlush(
                ProcessedEvent(eventId = event.eventId, consumerName = CONSUMER_NAME)
            )
        } catch (e: DataIntegrityViolationException) {
            log.info("event processed concurrently: eventId={} consumer={}", event.eventId, CONSUMER_NAME)
            return
        }

        smsSender.send(
            to = "buyer-of-order-${event.orderId}",
            message = "주문 ${event.orderId} 결제가 완료되었습니다. (paymentKey=${event.paymentKey})"
        )
    }

    private companion object {
        const val CONSUMER_NAME = "order-paid-sms-sender"
        val log = LoggerFactory.getLogger(OrderPaidEventListener::class.java)
    }
}
