package com.example.kotlinspringbootsample.post.controller

import com.example.kotlinspringbootsample.config.security.CustomAuthenticationManager
import com.example.kotlinspringbootsample.config.security.TokenProvider
import com.example.kotlinspringbootsample.post.dto.PostDeleteRequest
import com.example.kotlinspringbootsample.post.dto.PostDeletedResponse
import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.dto.PostResponse
import com.example.kotlinspringbootsample.post.exception.PostNotFoundException
import com.example.kotlinspringbootsample.post.service.PostService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*

@WebMvcTest(
    PostController::class,
    excludeAutoConfiguration = [
        UserDetailsServiceAutoConfiguration::class,
        SecurityAutoConfiguration::class
    ]
)
class PostControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @MockkBean private val postService: PostService,
    @MockkBean private val customAuthenticationManager: CustomAuthenticationManager,
    @MockkBean private val tokenProvider: TokenProvider
) : DescribeSpec({

    val mapper = jacksonObjectMapper()

    describe("GET /api/posts") {
        it("성공 응답을 sealed success 포맷으로 반환한다") {
            val posts = listOf(
                PostResponse("First Post", "This is the first post", "userA"),
                PostResponse("Second Post", "This is the second post", "userA")
            )
            val page = PageImpl(posts, PageRequest.of(0, 10), posts.size.toLong())

            every { postService.getAllPosts(0, 10) } returns page

            mockMvc.get("/api/posts")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.result") { value("success") }
                    jsonPath("$.code") { value("200") }
                    jsonPath("$.data.content[0].title") { value("First Post") }
                    jsonPath("$.data.content[1].title") { value("Second Post") }
                    jsonPath("$.data.totalElements") { value(2) }
                }

            verify { postService.getAllPosts(0, 10) }
        }
    }

    describe("GET /api/posts/{id}") {
        it("단건 조회 성공 시 sealed success 포맷을 반환한다") {
            val post = PostResponse("title", "First Post", "userA")
            every { postService.getPostById(1L) } returns post

            mockMvc.get("/api/posts/1")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.result") { value("success") }
                    jsonPath("$.data.title") { value("title") }
                    jsonPath("$.data.content") { value("First Post") }
                    jsonPath("$.data.username") { value("userA") }
                }

            verify { postService.getPostById(1L) }
        }

        it("게시물이 없으면 sealed failure 포맷으로 404를 반환한다") {
            every { postService.getPostById(999L) } throws PostNotFoundException("Post not found with id 999")

            mockMvc.get("/api/posts/999")
                .andExpect {
                    status { isNotFound() }
                    jsonPath("$.result") { value("failure") }
                    jsonPath("$.code") { value("404") }
                    jsonPath("$.message") { value("Post not found with id 999") }
                }
        }
    }

    describe("POST /api/posts") {
        it("생성 성공 시 sealed success 포맷으로 201을 반환한다") {
            val postRequest = PostRequest("New Post", "This is a new post", "userA", "password")
            val postResponse = PostResponse("New Post", "This is a new post", "userA")
            every { postService.createPost(postRequest) } returns postResponse

            mockMvc.post("/api/posts") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(postRequest)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.code") { value("201") }
                jsonPath("$.data.title") { value("New Post") }
            }

            verify { postService.createPost(postRequest) }
        }
    }

    describe("PUT /api/posts/{id}") {
        it("수정 성공 시 sealed success 포맷을 반환한다") {
            val postRequest = PostRequest("Updated Post", "This is an updated post", "userA", "password")
            val postResponse = PostResponse("Updated Post", "This is an updated post", "userA")
            every { postService.updatePost(1L, postRequest) } returns postResponse

            mockMvc.put("/api/posts/1") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(postRequest)
            }.andExpect {
                status { isOk() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.data.title") { value("Updated Post") }
            }

            verify { postService.updatePost(1L, postRequest) }
        }
    }

    describe("DELETE /api/posts/{id}") {
        it("삭제 성공 시 sealed success 포맷을 반환한다") {
            val postRequest = PostDeleteRequest("userA", "password")
            val postResponse = PostDeletedResponse("Post deleted successfully")
            every { postService.deletePost(1L, postRequest) } returns postResponse

            mockMvc.delete("/api/posts/1") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(postRequest)
            }.andExpect {
                status { isOk() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.data.message") { value("Post deleted successfully") }
            }

            verify { postService.deletePost(1L, postRequest) }
        }
    }
})
