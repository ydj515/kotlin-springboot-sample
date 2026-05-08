package com.example.kotlinspringbootsample.presentation.post.request

data class PostRequest(
    val title: String,
    val content: String,
    val username: String,
    val password: String
)
