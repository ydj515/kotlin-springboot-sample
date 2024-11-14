package com.example.kotlinspringbootsample

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KotlinSpringbootSampleApplication

fun main(args: Array<String>) {
    runApplication<KotlinSpringbootSampleApplication>(*args)
}
