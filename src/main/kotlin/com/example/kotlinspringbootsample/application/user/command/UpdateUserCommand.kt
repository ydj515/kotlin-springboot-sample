package com.example.kotlinspringbootsample.application.user.command

import com.example.kotlinspringbootsample.domain.user.UserType
import java.time.LocalDateTime

data class UpdateUserCommand(
    val id: Long,
    val username: String,
    val password: String? = null,
    val name: String? = null,
    val email: String? = null,
    val lastLoginAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val deletedAt: LocalDateTime? = null,
    val lastPasswordUpdatedAt: LocalDateTime? = null,
    val userType: UserType? = null,
    val trialCount: Int = 0
)
