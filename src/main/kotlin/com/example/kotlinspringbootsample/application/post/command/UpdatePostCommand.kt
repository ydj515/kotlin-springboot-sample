package com.example.kotlinspringbootsample.application.post.command

data class UpdatePostCommand(
    val id: Long,
    val title: String,
    val content: String,
    val username: String,
    val password: String
)
