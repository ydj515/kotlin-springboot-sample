package com.example.kotlinspringbootsample.presentation.user.response

import com.example.kotlinspringbootsample.application.user.result.UserRoleResult
import java.time.LocalDateTime

data class UserRoleResponse(
    val id: Long?,
    val name: String,
    val description: String,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(result: UserRoleResult): UserRoleResponse = UserRoleResponse(
            id = result.id,
            name = result.name,
            description = result.description,
            createdAt = result.createdAt,
            updatedAt = result.updatedAt
        )
    }
}
