package com.example.kotlinspringbootsample.application.post.command

data class FindPostsCommand(
    val page: Int,
    val size: Int
)
