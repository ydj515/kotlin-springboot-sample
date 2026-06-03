package com.example.kotlinspringbootsample.infrastructure.notification

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class ProcessedEventService(
    private val processedEventRepository: ProcessedEventRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun tryMarkProcessed(eventId: String, consumerName: String): Boolean {
        if (processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)) {
            log.info("event already processed: eventId={} consumer={}", eventId, consumerName)
            return false
        }

        return try {
            processedEventRepository.saveAndFlush(
                ProcessedEvent(eventId = eventId, consumerName = consumerName)
            )
            true
        } catch (e: DataIntegrityViolationException) {
            log.info("event processed concurrently: eventId={} consumer={}", eventId, consumerName)
            false
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ProcessedEventService::class.java)
    }
}
