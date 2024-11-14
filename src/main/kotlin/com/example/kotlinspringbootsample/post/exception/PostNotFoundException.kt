package com.example.kotlinspringbootsample.post.exception

class PostNotFoundException(
    override val message: String = "Post not found"
) : RuntimeException(message)