package com.example.kotlinspringbootsample.post.exception

class PostNotFoundException(
    message: String = "Post not found"
) : RuntimeException(message)
