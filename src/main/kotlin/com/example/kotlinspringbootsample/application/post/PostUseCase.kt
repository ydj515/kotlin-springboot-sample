package com.example.kotlinspringbootsample.application.post

import com.example.kotlinspringbootsample.application.post.command.CreatePostCommand
import com.example.kotlinspringbootsample.application.post.command.DeletePostCommand
import com.example.kotlinspringbootsample.application.post.command.FindPostsCommand
import com.example.kotlinspringbootsample.application.post.command.GetPostCommand
import com.example.kotlinspringbootsample.application.post.command.UpdatePostCommand
import com.example.kotlinspringbootsample.application.post.result.PostDeletedResult
import com.example.kotlinspringbootsample.application.post.result.PostResult
import com.example.kotlinspringbootsample.domain.post.Post
import com.example.kotlinspringbootsample.domain.post.exception.PostNotFoundException
import com.example.kotlinspringbootsample.domain.post.policy.PostAuthorPolicy
import com.example.kotlinspringbootsample.domain.post.repository.PostRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PostUseCase(
    private val postRepository: PostRepository,
    private val postAuthorPolicy: PostAuthorPolicy
) {

    fun getAllPosts(command: FindPostsCommand): Page<PostResult> =
        postRepository.findAllByDeletedAtIsNull(
            PageRequest.of(command.page, command.size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map { it.toResult() }

    fun getPostById(command: GetPostCommand): PostResult =
        findPostOrThrow(command.id).toResult()

    @Transactional
    fun createPost(command: CreatePostCommand): PostResult =
        postRepository.save(command.toPost()).toResult()

    @Transactional
    fun updatePost(command: UpdatePostCommand): PostResult =
        findPostOrThrow(command.id)
            .apply {
                postAuthorPolicy.validate(this, command.username, command.password)
                update(command.title, command.content, command.username, command.password)
            }
            .toResult()

    @Transactional
    fun deletePost(command: DeletePostCommand): PostDeletedResult {
        findPostOrThrow(command.id).apply {
            postAuthorPolicy.validate(this, command.username, command.password)
            markDeleted()
        }

        return PostDeletedResult("Post deleted successfully")
    }

    private fun findPostOrThrow(id: Long): Post =
        postRepository.findByIdOrNull(id) ?: throw PostNotFoundException("Post not found with id $id")
}
