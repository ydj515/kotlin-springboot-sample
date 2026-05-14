package com.example.kotlinspringbootsample.config.async

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@EnableAsync
@Configuration
class AsyncConfiguration {

    @Bean
    fun mdcTaskDecorator(): TaskDecorator = MdcTaskDecorator()

    @Bean(name = ["applicationTaskExecutor"])
    fun applicationTaskExecutor(taskDecorator: TaskDecorator): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 8
            queueCapacity = 100
            setThreadNamePrefix("app-async-")
            setTaskDecorator(taskDecorator)
            initialize()
        }
}
