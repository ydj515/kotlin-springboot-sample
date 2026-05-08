package com.example.kotlinspringbootsample.domain.post.exception

class PostNotFoundException(
    message: String = "Post not found"
) : RuntimeException(message)
