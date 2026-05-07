package com.example.kotlinspringbootsample.post.service

import com.example.kotlinspringbootsample.post.dto.PostDeleteRequest
import com.example.kotlinspringbootsample.post.dto.PostDeletedResponse
import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.dto.PostResponse
import com.example.kotlinspringbootsample.post.exception.PostNotFoundException
import com.example.kotlinspringbootsample.post.extensions.toPost
import com.example.kotlinspringbootsample.post.extensions.toPostResponse
import com.example.kotlinspringbootsample.post.model.Post
import com.example.kotlinspringbootsample.post.repository.PostRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PostService(
    private val postRepository: PostRepository
) {

    fun getAllPosts(page: Int, size: Int): Page<PostResponse> =
        postRepository.findAllByDeletedAtIsNull(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map { it.toPostResponse() }

    fun getPostById(id: Long): PostResponse = findPostOrThrow(id).toPostResponse()

    @Transactional
    fun createPost(postRequest: PostRequest): PostResponse =
        postRepository.save(postRequest.toPost()).toPostResponse()

    @Transactional
    fun updatePost(id: Long, postRequest: PostRequest): PostResponse =
        findPostOrThrow(id)
            .apply {
                requireAuthor(postRequest.username, postRequest.password)
                updateFromRequest(postRequest)
            }
            .toPostResponse()

    @Transactional
    fun deletePost(id: Long, postRequest: PostDeleteRequest): PostDeletedResponse {
        findPostOrThrow(id).apply {
            requireAuthor(postRequest.username, postRequest.password)
            markDeleted()
        }

        return PostDeletedResponse("Post deleted successfully")
    }

    private fun findPostOrThrow(id: Long): Post =
        postRepository.findByIdOrNull(id) ?: throw PostNotFoundException("Post not found with id $id")
}
