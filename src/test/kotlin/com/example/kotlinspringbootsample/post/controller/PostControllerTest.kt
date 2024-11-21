package com.example.kotlinspringbootsample.post.controller

import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.dto.PostResponse
import com.example.kotlinspringbootsample.post.service.PostService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*

@WebMvcTest(PostController::class)
class PostControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @MockkBean private val postService: PostService
) : DescribeSpec({

    val mapper = jacksonObjectMapper()


    describe("GET /api/posts") {
        context("유효한 요청이 전달되면") {
            it("200응답과 모든 posts를 리턴한다.") {
                // given
                val posts = listOf(
                    PostResponse("First Post", "This is the first post", "userA"),
                    PostResponse("Second Post", "This is the second post", "userA")
                )
                every { postService.getAllPosts() } returns posts

                // when then
                mockMvc.get("/api/posts")
                    .andExpect {
                        status { isOk() }
                        content {
                            json(mapper.writeValueAsString(posts))
                        }
                    }

                verify { postService.getAllPosts() }
            }
        }

    }

    describe(" GET / api / posts /{ id }") {
        context("유효한 요청이 전달되면") {
            it("200응답과 id에 맞는 post를 리턴한다.") {
                // given
                val post = PostResponse("title", "First Post", "This is the first post")
                every { postService.getPostById(1L) } returns post

                // when then
                mockMvc.get("/api/posts/1")
                    .andExpect {
                        status { isOk() }
                        content {
                            json(mapper.writeValueAsString(post))
                        }
                    }

                verify { postService.getPostById(1L) }
            }
        }

    }

    describe("POST /api/posts") {
        context("유효한 요청이 전달되면") {
            it("201 응답과 post가 생성된다.") {
                // given
                val postRequest = PostRequest("New Post", "This is a new post", "userA", "password")
                val postResponse = PostResponse("title", "New Post", "This is a new post")
                every { postService.createPost(postRequest) } returns postResponse

                // when then
                mockMvc.post("/api/posts") {
                    contentType = MediaType.APPLICATION_JSON
                    content = mapper.writeValueAsString(postRequest)
                }.andExpect {
                    status { isCreated() }
                    content {
                        json(mapper.writeValueAsString(postResponse))
                    }
                }

                verify { postService.createPost(postRequest) }
            }
        }
    }

    describe("PUT /api/posts/{id}") {
        context("유효한 요청이 전달되면") {
            it("id의 post가 업데이트 된다.") {
                // given
                val postRequest = PostRequest("Updated Post", "This is an updated post", "userA", "password")
                val postResponse = PostResponse("Updated Post", "This is an updated post", "userA")
                every { postService.updatePost(1L, postRequest) } returns postResponse

                // when then
                mockMvc.put("/api/posts/1") {
                    contentType = MediaType.APPLICATION_JSON
                    content = mapper.writeValueAsString(postRequest)
                }.andExpect {
                    status { isOk() }
                    content {
                        json(mapper.writeValueAsString(postResponse))
                    }
                }

                verify { postService.updatePost(1L, postRequest) }
            }
        }
    }

    describe("DELETE /api/posts/{id}") {
        context("유효한 요청이 전달되면") {
            it("204응답과 id의 post가 삭제된다.") {
                // given
                every { postService.deletePost(1L) } returns Unit

                // when then
                mockMvc.delete("/api/posts/1")
                    .andExpect {
                        status { isNoContent() }
                    }

                verify { postService.deletePost(1L) }
            }
        }
    }

})

