package com.example.kotlinspringbootsample.presentation.user.request

import com.example.kotlinspringbootsample.application.user.command.GetUserByUsernameCommand

data class UserSearchRequest(val username: String? = null) {
    fun toCommand(): GetUserByUsernameCommand = GetUserByUsernameCommand(username)
}
