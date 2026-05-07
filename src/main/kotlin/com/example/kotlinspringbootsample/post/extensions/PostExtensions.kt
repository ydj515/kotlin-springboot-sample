package com.example.kotlinspringbootsample.post.extensions

import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.dto.PostResponse
import com.example.kotlinspringbootsample.post.model.Post

fun PostRequest.toPost(): Post =
    Post(
        title = this.title,
        content = this.content,
        username = this.username,
        password = this.password
    )

fun Post.toPostResponse(): PostResponse =
    PostResponse(
        title = this.title,
        content = this.content,
        username = this.username
    )
