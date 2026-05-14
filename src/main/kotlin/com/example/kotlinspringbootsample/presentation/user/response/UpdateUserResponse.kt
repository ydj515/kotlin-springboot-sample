package com.example.kotlinspringbootsample.presentation.user.response

import com.example.kotlinspringbootsample.application.user.result.UpdateUserResult

data class UpdateUserResponse(
    val id: Long?,
    val updatedCount: Int
) {
    companion object {
        fun from(result: UpdateUserResult): UpdateUserResponse = UpdateUserResponse(
            id = result.id,
            updatedCount = result.updatedCount
        )
    }
}
