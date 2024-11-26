package com.example.kotlinspringbootsample.post.service

import com.example.kotlinspringbootsample.post.dto.PostDeleteRequest
import com.example.kotlinspringbootsample.post.dto.PostDeletedResponse
import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.dto.PostResponse
import com.example.kotlinspringbootsample.post.exception.PostNotFoundException
import com.example.kotlinspringbootsample.post.extensions.toPost
import com.example.kotlinspringbootsample.post.extensions.toPostResponse
import com.example.kotlinspringbootsample.post.repository.PostRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PostService(
    private val postRepository: PostRepository
) {

    fun getAllPosts(page: Int, size: Int): Page<PostResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val posts = postRepository.findAllByDeletedAtIsNull(pageable)

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

        if (post.username != postRequest.username || post.password != postRequest.password) {
            throw PostNotFoundException("username or password invalid")
        }

        post.updateFromRequest(postRequest)

        val savedPost = postRepository.save(post)

        return savedPost.toPostResponse()
    }

    @Transactional
    fun deletePost(id: Long, postRequest: PostDeleteRequest): PostDeletedResponse {
        val post = postRepository.findById(id).orElseThrow {
            PostNotFoundException("Post not found with id $id")
        }

        if (post.username != postRequest.username || post.password != postRequest.password) {
            throw PostNotFoundException("username or password invalid")
        }

        post.delete()
        postRepository.save(post)
        return PostDeletedResponse("Post deleted successfully")
    }
}
