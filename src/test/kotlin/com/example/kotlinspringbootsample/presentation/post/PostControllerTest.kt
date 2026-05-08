package com.example.kotlinspringbootsample.presentation.post

import com.example.kotlinspringbootsample.application.post.PostUseCase
import com.example.kotlinspringbootsample.application.post.command.CreatePostCommand
import com.example.kotlinspringbootsample.application.post.command.DeletePostCommand
import com.example.kotlinspringbootsample.application.post.command.FindPostsCommand
import com.example.kotlinspringbootsample.application.post.command.GetPostCommand
import com.example.kotlinspringbootsample.application.post.command.UpdatePostCommand
import com.example.kotlinspringbootsample.application.post.result.PostDeletedResult
import com.example.kotlinspringbootsample.application.post.result.PostResult
import com.example.kotlinspringbootsample.domain.post.exception.PostNotFoundException
import com.example.kotlinspringbootsample.infrastructure.security.CustomAuthenticationManager
import com.example.kotlinspringbootsample.infrastructure.security.TokenProvider
import com.example.kotlinspringbootsample.presentation.post.request.PostDeleteRequest
import com.example.kotlinspringbootsample.presentation.post.request.PostRequest
import com.example.kotlinspringbootsample.presentation.post.response.PostDeletedResponse
import com.example.kotlinspringbootsample.presentation.post.response.PostResponse
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
    @MockkBean private val postUseCase: PostUseCase,
    @MockkBean private val customAuthenticationManager: CustomAuthenticationManager,
    @MockkBean private val tokenProvider: TokenProvider
) : DescribeSpec({

    val mapper = jacksonObjectMapper()

    describe("GET /api/posts") {
        it("성공 응답을 sealed success 포맷으로 반환한다") {
            val posts = listOf(
                PostResult("First Post", "This is the first post", "userA"),
                PostResult("Second Post", "This is the second post", "userA")
            )
            val page = PageImpl(posts, PageRequest.of(0, 10), posts.size.toLong())

            every { postUseCase.getAllPosts(FindPostsCommand(0, 10)) } returns page

            mockMvc.get("/api/posts")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.result") { value("success") }
                    jsonPath("$.code") { value("200") }
                    jsonPath("$.data.content[0].title") { value("First Post") }
                    jsonPath("$.data.content[1].title") { value("Second Post") }
                    jsonPath("$.data.totalElements") { value(2) }
                }

            verify { postUseCase.getAllPosts(FindPostsCommand(0, 10)) }
        }
    }

    describe("GET /api/posts/{id}") {
        it("단건 조회 성공 시 sealed success 포맷을 반환한다") {
            val post = PostResult("title", "First Post", "userA")
            every { postUseCase.getPostById(GetPostCommand(1L)) } returns post

            mockMvc.get("/api/posts/1")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.result") { value("success") }
                    jsonPath("$.data.title") { value("title") }
                    jsonPath("$.data.content") { value("First Post") }
                    jsonPath("$.data.username") { value("userA") }
                }

            verify { postUseCase.getPostById(GetPostCommand(1L)) }
        }

        it("게시물이 없으면 sealed failure 포맷으로 404를 반환한다") {
            every { postUseCase.getPostById(GetPostCommand(999L)) } throws PostNotFoundException("Post not found with id 999")

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
            val createCommand = CreatePostCommand("New Post", "This is a new post", "userA", "password")
            val postResponse = PostResult("New Post", "This is a new post", "userA")
            every { postUseCase.createPost(createCommand) } returns postResponse

            mockMvc.post("/api/posts") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(postRequest)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.code") { value("201") }
                jsonPath("$.data.title") { value("New Post") }
            }

            verify { postUseCase.createPost(createCommand) }
        }
    }

    describe("PUT /api/posts/{id}") {
        it("수정 성공 시 sealed success 포맷을 반환한다") {
            val postRequest = PostRequest("Updated Post", "This is an updated post", "userA", "password")
            val updateCommand = UpdatePostCommand(1L, "Updated Post", "This is an updated post", "userA", "password")
            val postResponse = PostResult("Updated Post", "This is an updated post", "userA")
            every { postUseCase.updatePost(updateCommand) } returns postResponse

            mockMvc.put("/api/posts/1") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(postRequest)
            }.andExpect {
                status { isOk() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.data.title") { value("Updated Post") }
            }

            verify { postUseCase.updatePost(updateCommand) }
        }
    }

    describe("DELETE /api/posts/{id}") {
        it("삭제 성공 시 sealed success 포맷을 반환한다") {
            val postRequest = PostDeleteRequest("userA", "password")
            val deleteCommand = DeletePostCommand(1L, "userA", "password")
            val postResponse = PostDeletedResult("Post deleted successfully")
            every { postUseCase.deletePost(deleteCommand) } returns postResponse

            mockMvc.delete("/api/posts/1") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(postRequest)
            }.andExpect {
                status { isOk() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.data.message") { value("Post deleted successfully") }
            }

            verify { postUseCase.deletePost(deleteCommand) }
        }
    }
})
