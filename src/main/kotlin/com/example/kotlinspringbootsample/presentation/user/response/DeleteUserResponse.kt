package com.example.kotlinspringbootsample.presentation.user.response

import com.example.kotlinspringbootsample.application.user.result.DeleteUserResult

data class DeleteUserResponse(
    val id: Long?,
    val deletedCount: Int
) {
    companion object {
        fun from(result: DeleteUserResult): DeleteUserResponse = DeleteUserResponse(
            id = result.id,
            deletedCount = result.deletedCount
        )
    }
}
