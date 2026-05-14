package com.example.kotlinspringbootsample.presentation.user.request

import com.example.kotlinspringbootsample.application.user.command.DeleteUserCommand

data class DeleteUserRequest(val id: Long) {
    fun toCommand(): DeleteUserCommand = DeleteUserCommand(id)

    companion object {
        fun from(id: Long): DeleteUserRequest = DeleteUserRequest(id)
    }
}
