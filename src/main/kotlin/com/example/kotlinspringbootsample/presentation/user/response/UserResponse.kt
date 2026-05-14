package com.example.kotlinspringbootsample.presentation.user.response

import com.example.kotlinspringbootsample.application.user.result.UserResult
import java.time.LocalDateTime

data class UserResponse(
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
    val roles: List<UserRoleResponse>
) {
    companion object {
        fun from(result: UserResult): UserResponse = UserResponse(
            id = result.id,
            username = result.username,
            name = result.name,
            email = result.email,
            userType = result.userType,
            lastLoginAt = result.lastLoginAt,
            createdAt = result.createdAt,
            updatedAt = result.updatedAt,
            deletedAt = result.deletedAt,
            lastPasswordUpdatedAt = result.lastPasswordUpdatedAt,
            trialCount = result.trialCount,
            roles = result.roles.map(UserRoleResponse::from)
        )
    }
}
