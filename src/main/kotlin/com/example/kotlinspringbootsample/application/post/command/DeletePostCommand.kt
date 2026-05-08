package com.example.kotlinspringbootsample.application.post.command

data class DeletePostCommand(
    val id: Long,
    val username: String,
    val password: String
)
