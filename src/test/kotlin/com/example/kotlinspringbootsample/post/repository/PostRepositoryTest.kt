package com.example.kotlinspringbootsample.post.repository

import com.example.kotlinspringbootsample.config.ClockConfig
import com.example.kotlinspringbootsample.post.model.Post
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.*

@DataJpaTest
@Import(ClockConfig::class)
class PostRepositoryTest(
    @Autowired val postRepository: PostRepository,
    @Autowired val clock: Clock
) : DescribeSpec({

    lateinit var post1: Post
    lateinit var post2: Post
    lateinit var deletedPost: Post
    val fixedInstant = Instant.parse("2023-12-06T12:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneId.of(ZoneOffset.UTC.id))

    beforeTest {
        val fixedNow = LocalDateTime.now(fixedClock)

        post1 = Post(id = 1L, title = "Post 1", content = "Content 1", username = "user1", password = "pass1")
        post2 = Post(id = 2L, title = "Post 2", content = "Content 2", username = "user2", password = "pass2", )
        deletedPost = Post(id = 3L, title = "Post 3", content = "Content 3", username = "user3", password = "pass3", deletedAt = fixedNow)

        postRepository.saveAll(listOf(post1, post2, deletedPost))
    }

    describe("PostRepository") {
        context("findAllByDeletedAtIsNull()를 호출하면") {
            it("삭제되지 않은 게시물만 반환해야 한다") {
                // given
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))

                // when
                val result = postRepository.findAllByDeletedAtIsNull(pageable)

                // then
                result.totalElements shouldBe 2
            }
        }

        context("findAllByDeletedAtIsNull() 호출시 데이터가 없다면") {
            it("빈 결과를 반환해야 한다") {
                // given
                postRepository.deleteAll()

                // when
                val result = postRepository.findAllByDeletedAtIsNull(PageRequest.of(0, 10))

                // then
                result.totalElements shouldBe 0
                result.content shouldBe emptyList()
            }
        }
    }

    afterTest {
        postRepository.deleteAll()
    }
})
