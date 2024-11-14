package com.example.kotlinspringbootsample.post.controller

import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.dto.PostResponse
import com.example.kotlinspringbootsample.post.service.PostService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/posts")
class PostController(
    private val postService: PostService
) {

    @GetMapping
    fun getAllPosts(): List<PostResponse> {
        return postService.getAllPosts()
    }

    @GetMapping("/{id}")
    fun getPostById(@PathVariable id: Long): ResponseEntity<PostResponse> {
        val post = postService.getPostById(id)
        return ResponseEntity.ok(post)
    }

    @PostMapping
    fun createPost(@RequestBody postRequest: PostRequest): ResponseEntity<PostResponse> {
        val post = postService.createPost(postRequest)
        return ResponseEntity.ok(post)
    }

    @PutMapping("/{id}")
    fun updatePost(
        @PathVariable id: Long,
        @RequestBody postRequest: PostRequest
    ): ResponseEntity<PostResponse> {
        val updatedPost = postService.updatePost(id, postRequest)
        return ResponseEntity.ok(updatedPost)
    }

    @DeleteMapping("/{id}")
    fun deletePost(@PathVariable id: Long): ResponseEntity<Void> {
        postService.deletePost(id)
        return ResponseEntity.noContent().build()
    }
}