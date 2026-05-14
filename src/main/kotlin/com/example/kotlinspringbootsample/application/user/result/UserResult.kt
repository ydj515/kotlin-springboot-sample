package com.example.kotlinspringbootsample.application.user.result

import com.example.kotlinspringbootsample.domain.user.User
import java.time.LocalDateTime

data class UserResult(
    val id: Long?,
    val username: String,
    val name: String?,
    val email: String?,
    val userType: String?,
    val lastLoginAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val deletedAt: LocalDateTime?,
    val lastPasswordUpdatedAt: LocalDateTime?,
    val trialCount: Int,
    val roles: List<UserRoleResult>
) {
    companion object {
        fun from(user: User): UserResult = UserResult(
            id = user.id,
            username = user.username,
            name = user.name,
            email = user.email,
            userType = user.userType?.name,
            lastLoginAt = user.lastLoginAt,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            deletedAt = user.deletedAt,
            lastPasswordUpdatedAt = user.lastPasswordUpdatedAt,
            trialCount = user.trialCount,
            roles = user.roles
                .map(UserRoleResult::from)
                .sortedBy { it.id ?: Long.MAX_VALUE }
        )
    }
}
