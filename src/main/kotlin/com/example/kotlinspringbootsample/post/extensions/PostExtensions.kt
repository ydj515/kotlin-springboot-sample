package com.example.kotlinspringbootsample.post.extensions

import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.dto.PostResponse
import com.example.kotlinspringbootsample.post.model.Post

// PostRequest -> Post
fun PostRequest.toPost(): Post {
    return Post(
        title = this.title,
        content = this.content,
        username = this.username,
        password = this.password
    )
}

// Post -> PostResponse
fun Post.toPostResponse(): PostResponse {
    return PostResponse(
        title = this.title,
        content = this.content,
        username = this.username
    )
}