package com.example.kotlinspringbootsample.post.service

import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.dto.PostResponse
import com.example.kotlinspringbootsample.post.exception.PostNotFoundException
import com.example.kotlinspringbootsample.post.model.Post
import com.example.kotlinspringbootsample.post.repository.PostRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class PostService(
    private val postRepository: PostRepository
) {

    fun getAllPosts(): List<PostResponse> {
        var posts = postRepository.findAll()
        return posts.map { post ->
            PostResponse(
                title = post.title,
                content = post.content,
                username = post.username
            )
        }
    }

    fun getPostById(id: Long): PostResponse {
        val post = postRepository.findById(id).orElseThrow {
            PostNotFoundException("Post not found with id $id")
        }

        return PostResponse(
            title = post.title,
            content = post.content,
            username = post.username
        )
    }

    @Transactional
    fun createPost(postRequest: PostRequest): PostResponse {
        val post = Post(
            title = postRequest.title,
            content = postRequest.content,
            username = postRequest.username,
            password = postRequest.password
        )

        val savedPost = postRepository.save(post)

        return PostResponse(
            title = savedPost.title,
            content = savedPost.content,
            username = savedPost.username
        )
    }

    @Transactional
    fun updatePost(id: Long, postRequest: PostRequest): PostResponse {
        val post = postRepository.findById(id).orElseThrow {
            PostNotFoundException("Post not found with id $id")
        }
        post.title = postRequest.title
        post.content = postRequest.content
        post.username = postRequest.username
        post.password = postRequest.password

        val savedPost = postRepository.save(post)

        return PostResponse(
            title = savedPost.title,
            content = savedPost.content,
            username = savedPost.username
        )
    }

    @Transactional
    fun deletePost(id: Long) {
        val post = postRepository.findById(id).orElseThrow {
            PostNotFoundException("Post not found with id $id")
        }

        postRepository.delete(post)
    }
}
