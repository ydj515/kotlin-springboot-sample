package com.example.kotlinspringbootsample.presentation.user.request

import com.example.kotlinspringbootsample.application.user.command.UpdateUserCommand
import com.example.kotlinspringbootsample.domain.user.UserType
import java.time.LocalDateTime

data class UpdateUserRequest(
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
) {
    fun toCommand(): UpdateUserCommand = UpdateUserCommand(
        id = id,
        username = username,
        password = password,
        name = name,
        email = email,
        lastLoginAt = lastLoginAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastPasswordUpdatedAt = lastPasswordUpdatedAt,
        userType = userType,
        trialCount = trialCount
    )
}
