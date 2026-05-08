package com.example.kotlinspringbootsample.domain.post.policy

import com.example.kotlinspringbootsample.domain.post.Post
import com.example.kotlinspringbootsample.domain.post.exception.PostNotFoundException
import org.springframework.stereotype.Component

@Component
class PostAuthorPolicy {
    fun validate(post: Post, username: String, password: String) {
        if (post.username != username || post.password != password) {
            throw PostNotFoundException("username or password invalid")
        }
    }
}
