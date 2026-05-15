package com.example.kotlinspringbootsample.infrastructure.notification

import com.example.kotlinspringbootsample.domain.notification.SmsSender
import com.example.kotlinspringbootsample.domain.order.event.OrderPaidEvent
import com.example.kotlinspringbootsample.support.MySqlIntegrationTestSupport
import com.ninjasquad.springmockk.MockkBean
import io.mockk.confirmVerified
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class OrderPaidEventListenerIntegrationTest @Autowired constructor(
    private val eventPublisher: ApplicationEventPublisher,
    private val processedEventRepository: ProcessedEventRepository,
    @MockkBean(relaxed = true) private val smsSender: SmsSender,
    transactionManager: PlatformTransactionManager
) : MySqlIntegrationTestSupport() {

    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val createdEventIds = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        justRun { smsSender.send(any(), any()) }
    }

    @AfterEach
    fun cleanup() {
        createdEventIds.forEach { eventId ->
            processedEventRepository.deleteById(ProcessedEventId(eventId, CONSUMER_NAME))
        }
        createdEventIds.clear()
    }

    @Test
    fun `OrderPaidEvent 발행 시 SmsSender가 1회 호출되고 processed_events에 기록된다`() {
        val event = sampleEvent()

        publishInTransaction(event)
        createdEventIds += event.eventId

        verify(exactly = 1) {
            smsSender.send(
                "buyer-of-order-${event.orderId}",
                match { it.contains(event.orderId.toString()) && it.contains(event.paymentKey) }
            )
        }
        val exists = processedEventRepository.existsByEventIdAndConsumerName(event.eventId, CONSUMER_NAME)
        assert(exists) { "processed_events should contain the event" }
    }

    @Test
    fun `같은 OrderPaidEvent를 두 번 발행해도 SmsSender는 1번만 호출된다`() {
        val event = sampleEvent()

        publishInTransaction(event)
        publishInTransaction(event)
        createdEventIds += event.eventId

        verify(exactly = 1) { smsSender.send(any(), any()) }
        confirmVerified(smsSender)
    }

    private fun publishInTransaction(event: OrderPaidEvent) {
        transactionTemplate.executeWithoutResult {
            eventPublisher.publishEvent(event)
        }
    }

    private fun sampleEvent(): OrderPaidEvent = OrderPaidEvent(
        eventId = UUID.randomUUID().toString(),
        orderId = 999L,
        paymentId = 99L,
        paymentKey = "MOCK-PG-${UUID.randomUUID()}",
        amount = BigDecimal("12345.00"),
        paidAt = LocalDateTime.now()
    )

    private companion object {
        const val CONSUMER_NAME = "order-paid-sms-sender"
    }
}
