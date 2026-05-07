package com.example.kotlinspringbootsample.post.controller

import com.example.kotlinspringbootsample.common.dto.ApiResult
import com.example.kotlinspringbootsample.common.dto.ResultCode
import com.example.kotlinspringbootsample.post.dto.PostDeleteRequest
import com.example.kotlinspringbootsample.post.dto.PostDeletedResponse
import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.dto.PostResponse
import com.example.kotlinspringbootsample.post.service.PostService
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/posts")
class PostController(
    private val postService: PostService
) {

    @GetMapping
    fun getPosts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ApiResult.Success<Page<PostResponse>> =
        ApiResult.success(postService.getAllPosts(page, size))

    @GetMapping("/{id}")
    fun getPostById(@PathVariable id: Long): ApiResult.Success<PostResponse> =
        ApiResult.success(postService.getPostById(id))

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun createPost(@RequestBody postRequest: PostRequest): ApiResult.Success<PostResponse> =
        ApiResult.success(postService.createPost(postRequest), ResultCode.CREATED)

    @PutMapping("/{id}")
    fun updatePost(
        @PathVariable id: Long,
        @RequestBody postRequest: PostRequest
    ): ApiResult.Success<PostResponse> =
        ApiResult.success(postService.updatePost(id, postRequest))

    @DeleteMapping("/{id}")
    fun deletePost(
        @PathVariable id: Long,
        @RequestBody postRequest: PostDeleteRequest
    ): ApiResult.Success<PostDeletedResponse> =
        ApiResult.success(postService.deletePost(id, postRequest))
}
