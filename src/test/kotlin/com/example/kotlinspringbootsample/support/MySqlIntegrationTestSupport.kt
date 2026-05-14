package com.example.kotlinspringbootsample.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class MySqlIntegrationTestSupport {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val MYSQL: MySQLContainer<*> = MySQLContainer("mysql:8.4.3")
            .withDatabaseName("integration_test_db")
            .withUsername("integration_test_user")
            .withPassword("integration_test_password")
            .withUrlParam("serverTimezone", "Asia/Seoul")
            .withUrlParam("characterEncoding", "UTF-8")
    }
}
