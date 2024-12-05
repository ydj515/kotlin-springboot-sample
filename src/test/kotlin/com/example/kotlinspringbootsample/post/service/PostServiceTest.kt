package com.example.kotlinspringbootsample.post.service

import com.example.kotlinspringbootsample.config.ClockConfig
import com.example.kotlinspringbootsample.post.dto.PostDeleteRequest
import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.model.Post
import com.example.kotlinspringbootsample.post.repository.PostRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.*
import java.util.*

@Import(ClockConfig::class)
class PostServiceTest(
    @Autowired val clock: Clock
) : BehaviorSpec({

    val postRepository = mockk<PostRepository>()
    val postService = PostService(postRepository)
    val fixedInstant = Instant.parse("2023-12-06T12:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneId.of(ZoneOffset.UTC.id))

    Given("유효한 페이지 번호와 페이지 크기가 주어졌을 때") {
        val page = 0
        val size = 10
        val posts = PageImpl(
            listOf(
                Post(1L, "Test Title", "Test Content", "user1", "password1"),
                Post(2L, "Another Title", "Another Content", "user2", "password2")
            )
        )
        every {
            postRepository.findAllByDeletedAtIsNull(
                PageRequest.of(
                    page,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
                )
            )
        } returns posts

        When("getAllPosts 메서드가 호출되면") {
            val result = postService.getAllPosts(page, size)

            Then("PostRepository의 findAllByDeletedAtIsNull 메서드가 호출된다.") {
                verify { postRepository.findAllByDeletedAtIsNull(any()) }
            }

            Then("결과로 PostResponse의 Page가 반환된다.") {
                result.content.size shouldBe posts.content.size
                result.content[0].title shouldBe posts.content[0].title
            }
        }
    }

    Given("유효한 게시물 ID가 주어졌을 때") {
        val postId = 1L
        val post = Post(postId, "Test Title", "Test Content", "user1", "password1")
        every { postRepository.findById(postId) } returns Optional.of(post)

        When("getPostById 메서드가 호출되면") {
            val result = postService.getPostById(postId)

            Then("PostRepository의 findById 메서드가 호출된다.") {
                verify { postRepository.findById(postId) }
            }

            Then("결과로 PostResponse가 반환된다.") {
                result.title shouldBe post.title
                result.content shouldBe post.content
            }
        }
    }

    Given("새 게시물 생성 요청이 주어졌을 때") {
        val fixedNow = LocalDateTime.now(fixedClock)

        val postRequest = PostRequest("Test Title", "Test Content", "user1", "password1")
        val post = Post(
            id = 1L,
            title = postRequest.title,
            content = postRequest.content,
            username = postRequest.username,
            password = postRequest.password,
        )

        every { postRepository.save(any()) } returns post

        When("createPost 메서드가 호출되면") {
            val result = postService.createPost(postRequest)

            Then("PostRepository의 save 메서드가 호출된다.") {
                verify { postRepository.save(any()) }
            }

            Then("결과로 PostResponse가 반환된다.") {
                result.title shouldBe postRequest.title
                result.content shouldBe postRequest.content
            }
        }
    }

    Given("유효한 게시물 ID와 업데이트 요청이 주어졌을 때") {
        val postId = 1L
        val existingPost = Post(postId, "Old Title", "Old Content", "user1", "password1")
        val postRequest = PostRequest("New Title", "New Content", "user1", "password1")
        every { postRepository.findById(postId) } returns Optional.of(existingPost)
        every { postRepository.save(any()) } returns existingPost

        When("updatePost 메서드가 호출되면") {
            val result = postService.updatePost(postId, postRequest)

            Then("PostRepository의 findById와 save 메서드가 호출된다.") {
                verify { postRepository.findById(postId) }
                verify { postRepository.save(existingPost) }
            }

            Then("결과로 업데이트된 PostResponse가 반환된다.") {
                result.title shouldBe postRequest.title
                result.content shouldBe postRequest.content
            }
        }
    }

    Given("유효한 게시물 ID와 삭제 요청이 주어졌을 때") {
        val postId = 1L
        val existingPost = Post(postId, "Test Title", "Test Content", "user1", "password1")
        val deleteRequest = PostDeleteRequest("user1", "password1")
        every { postRepository.findById(postId) } returns Optional.of(existingPost)
        every { postRepository.save(existingPost) } returns existingPost

        When("deletePost 메서드가 호출되면") {
            val result = postService.deletePost(postId, deleteRequest)

            Then("PostRepository의 findById와 save 메서드가 호출된다.") {
                verify { postRepository.findById(postId) }
                verify { postRepository.save(existingPost) }
            }

            Then("결과로 성공 메시지가 반환된다.") {
                result.message shouldBe "Post deleted successfully"
            }
        }
    }
})
