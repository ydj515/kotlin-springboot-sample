package com.example.kotlinspringbootsample.infrastructure.outbox

import com.example.kotlinspringbootsample.domain.order.event.OrderPaidEvent
import com.example.kotlinspringbootsample.domain.outbox.OutboxEvent
import com.example.kotlinspringbootsample.domain.outbox.repository.OutboxEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.math.min

@Component
class OutboxPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper
) {

    @Scheduled(fixedDelayString = "\${app.outbox.publisher.fixed-delay-ms:1000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun publishBatch() {
        val now = LocalDateTime.now()
        val batch = outboxEventRepository.findPendingForUpdate(now, BATCH_SIZE)
        if (batch.isEmpty()) return

        batch.forEach { event ->
            try {
                dispatch(event)
                event.markPublished(LocalDateTime.now())
            } catch (e: Exception) {
                handleFailure(event, e)
            }
        }
        outboxEventRepository.saveAll(batch)
    }

    private fun dispatch(event: OutboxEvent) {
        val deserialized = when (event.eventType) {
            OrderPaidEvent.EVENT_TYPE -> objectMapper.readValue(event.payload, OrderPaidEvent::class.java)
            else -> throw IllegalStateException("unknown event type: ${event.eventType}")
        }
        applicationEventPublisher.publishEvent(deserialized)
        log.info("outbox published: id={} type={} eventId={}", event.id, event.eventType, event.eventId)
    }

    private fun handleFailure(event: OutboxEvent, e: Exception) {
        val nextAttempt = LocalDateTime.now().plusSeconds(backoffSeconds(event.retryCount + 1))
        event.markRetry(error = e.message ?: e.javaClass.simpleName, nextAttempt = nextAttempt)
        if (event.retryCount >= MAX_RETRY) {
            event.markFailed(error = "max retry exceeded: ${e.message ?: e.javaClass.simpleName}")
        }
        log.warn("outbox publish failed: id={} retry={} reason={}", event.id, event.retryCount, e.message)
    }

    private fun backoffSeconds(attempt: Int): Long =
        min(MAX_BACKOFF_SECONDS, 2L shl (attempt - 1).coerceIn(0, 8))

    private companion object {
        val log = LoggerFactory.getLogger(OutboxPublisher::class.java)
        const val BATCH_SIZE = 10
        const val MAX_RETRY = 5
        const val MAX_BACKOFF_SECONDS = 60L
    }
}
