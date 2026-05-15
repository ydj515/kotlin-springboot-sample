package com.example.kotlinspringbootsample.infrastructure.notification

import com.example.kotlinspringbootsample.domain.notification.SmsSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FakeSmsSender : SmsSender {
    override fun send(to: String, message: String) {
        log.info("[FAKE SMS] to={} message={}", to, message)
    }

    private companion object {
        val log = LoggerFactory.getLogger(FakeSmsSender::class.java)
    }
}
