package com.example.kotlinspringbootsample.application.user.result

import com.example.kotlinspringbootsample.domain.user.Role
import java.time.LocalDateTime

data class UserRoleResult(
    val id: Long?,
    val name: String,
    val description: String,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(role: Role): UserRoleResult = UserRoleResult(
            id = role.id,
            name = role.name,
            description = role.description,
            createdAt = role.createdAt,
            updatedAt = role.updatedAt
        )
    }
}
