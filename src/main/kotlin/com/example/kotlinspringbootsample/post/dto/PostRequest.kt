package com.example.kotlinspringbootsample.post.dto

data class PostRequest(
    val title: String,
    val content: String,
    val username: String,
    val password: String
)
