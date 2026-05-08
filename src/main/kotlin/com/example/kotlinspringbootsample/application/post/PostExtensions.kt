package com.example.kotlinspringbootsample.application.post

import com.example.kotlinspringbootsample.application.post.command.CreatePostCommand
import com.example.kotlinspringbootsample.application.post.result.PostResult
import com.example.kotlinspringbootsample.domain.post.Post

internal fun CreatePostCommand.toPost(): Post =
    Post(
        title = title,
        content = content,
        username = username,
        password = password
    )

internal fun Post.toResult(): PostResult =
    PostResult(
        title = title,
        content = content,
        username = username
    )
