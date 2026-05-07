package com.example.kotlinspringbootsample.post.service

import com.example.kotlinspringbootsample.post.dto.PostDeleteRequest
import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.exception.PostNotFoundException
import com.example.kotlinspringbootsample.post.model.Post
import com.example.kotlinspringbootsample.post.repository.PostRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.util.Optional

class PostServiceTest : BehaviorSpec({

    val postRepository = mockk<PostRepository>()
    val postService = PostService(postRepository)

    beforeTest {
        clearMocks(postRepository)
    }

    Given("게시물 목록 조회를 요청하면") {
        When("삭제되지 않은 게시물이 존재하면") {
            Then("createdAt 내림차순으로 조회해 응답 DTO로 변환한다") {
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                val posts = listOf(
                    Post(id = 1L, title = "Test Title", content = "Test Content", username = "user1", password = "password1"),
                    Post(id = 2L, title = "Another Title", content = "Another Content", username = "user2", password = "password2")
                )

                every { postRepository.findAllByDeletedAtIsNull(pageable) } returns PageImpl(posts, pageable, posts.size.toLong())

                val result = postService.getAllPosts(0, 10)

                verify(exactly = 1) { postRepository.findAllByDeletedAtIsNull(pageable) }
                result.content shouldHaveSize 2
                result.content[0].title shouldBe "Test Title"
                result.content[1].username shouldBe "user2"
            }
        }
    }

    Given("게시물 단건 조회를 요청하면") {
        When("게시물이 존재하면") {
            Then("게시물 정보를 반환한다") {
                val postId = 1L
                val post = Post(id = postId, title = "Test Title", content = "Test Content", username = "user1", password = "password1")

                every { postRepository.findById(postId) } returns Optional.of(post)

                val result = postService.getPostById(postId)

                verify(exactly = 1) { postRepository.findById(postId) }
                result.title shouldBe "Test Title"
                result.content shouldBe "Test Content"
                result.username shouldBe "user1"
            }
        }

        When("게시물이 존재하지 않으면") {
            Then("게시물을 찾을 수 없다는 예외를 던진다") {
                every { postRepository.findById(999L) } returns Optional.empty()

                val exception = shouldThrow<PostNotFoundException> {
                    postService.getPostById(999L)
                }

                exception.message shouldBe "Post not found with id 999"
            }
        }
    }

    Given("게시물 생성 요청이 들어오면") {
        When("유효한 요청 값이 전달되면") {
            Then("요청 값을 저장하고 응답 DTO를 반환한다") {
                val request = PostRequest("New Title", "New Content", "user1", "password1")
                val savedPost = Post(id = 1L, title = request.title, content = request.content, username = request.username, password = request.password)
                val savedPostSlot = slot<Post>()

                every { postRepository.save(capture(savedPostSlot)) } returns savedPost

                val result = postService.createPost(request)

                verify(exactly = 1) { postRepository.save(any()) }
                savedPostSlot.captured.title shouldBe "New Title"
                savedPostSlot.captured.username shouldBe "user1"
                result.title shouldBe "New Title"
                result.content shouldBe "New Content"
            }
        }
    }

    Given("게시물 수정 요청이 들어오면") {
        When("작성자 정보가 일치하면") {
            Then("게시물을 수정하고 save 없이 변경 감지로 반영한다") {
                val postId = 1L
                val existingPost = Post(id = postId, title = "Old Title", content = "Old Content", username = "user1", password = "password1")
                val request = PostRequest("New Title", "New Content", "user1", "password1")

                every { postRepository.findById(postId) } returns Optional.of(existingPost)

                val result = postService.updatePost(postId, request)

                verify(exactly = 1) { postRepository.findById(postId) }
                verify(exactly = 0) { postRepository.save(any()) }
                existingPost.title shouldBe "New Title"
                existingPost.content shouldBe "New Content"
                result.title shouldBe "New Title"
                result.content shouldBe "New Content"
            }
        }

        When("작성자 정보가 일치하지 않으면") {
            Then("게시물을 수정하지 않고 예외를 던진다") {
                val postId = 1L
                val existingPost = Post(id = postId, title = "Old Title", content = "Old Content", username = "user1", password = "password1")
                val request = PostRequest("New Title", "New Content", "another-user", "wrong-password")

                every { postRepository.findById(postId) } returns Optional.of(existingPost)

                val exception = shouldThrow<PostNotFoundException> {
                    postService.updatePost(postId, request)
                }

                exception.message shouldBe "username or password invalid"
                existingPost.title shouldBe "Old Title"
                verify(exactly = 0) { postRepository.save(any()) }
            }
        }
    }

    Given("게시물 삭제 요청이 들어오면") {
        When("작성자 정보가 일치하면") {
            Then("삭제 시각을 기록하고 성공 메시지를 반환한다") {
                val postId = 1L
                val existingPost = Post(id = postId, title = "Test Title", content = "Test Content", username = "user1", password = "password1")
                val request = PostDeleteRequest("user1", "password1")

                every { postRepository.findById(postId) } returns Optional.of(existingPost)

                val result = postService.deletePost(postId, request)

                verify(exactly = 1) { postRepository.findById(postId) }
                verify(exactly = 0) { postRepository.save(any()) }
                existingPost.deletedAt.shouldNotBeNull()
                result.message shouldBe "Post deleted successfully"
            }
        }

        When("작성자 정보가 일치하지 않으면") {
            Then("삭제 시각을 기록하지 않고 예외를 던진다") {
                val postId = 1L
                val existingPost = Post(id = postId, title = "Test Title", content = "Test Content", username = "user1", password = "password1")
                val request = PostDeleteRequest("user1", "wrong-password")

                every { postRepository.findById(postId) } returns Optional.of(existingPost)

                val exception = shouldThrow<PostNotFoundException> {
                    postService.deletePost(postId, request)
                }

                exception.message shouldBe "username or password invalid"
                existingPost.deletedAt shouldBe null
            }
        }
    }
})
