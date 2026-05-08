package com.example.kotlinspringbootsample.application.post.command

data class CreatePostCommand(
    val title: String,
    val content: String,
    val username: String,
    val password: String
)
