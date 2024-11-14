package com.example.kotlinspringbootsample.post.service

import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.dto.PostResponse
import com.example.kotlinspringbootsample.post.exception.PostNotFoundException
import com.example.kotlinspringbootsample.post.extensions.toPost
import com.example.kotlinspringbootsample.post.extensions.toPostResponse
import com.example.kotlinspringbootsample.post.repository.PostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PostService(
    private val postRepository: PostRepository
) {

    fun getAllPosts(): List<PostResponse> {
        var posts = postRepository.findAll()
        return posts.map { it.toPostResponse() }
    }

    fun getPostById(id: Long): PostResponse {
        val post = postRepository.findById(id).orElseThrow {
            PostNotFoundException("Post not found with id $id")
        }

        return post.toPostResponse()
    }

    @Transactional
    fun createPost(postRequest: PostRequest): PostResponse {
        val post = postRequest.toPost()

        val savedPost = postRepository.save(post)

        return savedPost.toPostResponse()
    }

    @Transactional
    fun updatePost(id: Long, postRequest: PostRequest): PostResponse {
        val post = postRepository.findById(id).orElseThrow {
            PostNotFoundException("Post not found with id $id")
        }

        post.updateFromRequest(postRequest)

        val savedPost = postRepository.save(post)

        return savedPost.toPostResponse()
    }

    @Transactional
    fun deletePost(id: Long) {
        val post = postRepository.findById(id).orElseThrow {
            PostNotFoundException("Post not found with id $id")
        }

        postRepository.delete(post)
    }
}
