package com.example.kotlinspringbootsample

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class KotlinSpringbootSampleApplication

fun main(args: Array<String>) {
    runApplication<KotlinSpringbootSampleApplication>(*args)
}
