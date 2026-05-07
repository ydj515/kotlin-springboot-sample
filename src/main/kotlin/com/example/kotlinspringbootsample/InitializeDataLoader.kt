package com.example.kotlinspringbootsample

import com.example.kotlinspringbootsample.post.model.Post
import com.example.kotlinspringbootsample.post.repository.PostRepository
import com.example.kotlinspringbootsample.user.model.User
import com.example.kotlinspringbootsample.user.repository.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class InitializeDataLoader(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        seedUsers()
        seedPosts()
    }

    private fun seedUsers() {
        val usersToSave = sampleUsers
            .filterNot { userRepository.existsByUsername(it.username) }
            .map {
                User(
                    username = it.username,
                    password = passwordEncoder.encode(it.password)
                )
            }

        if (usersToSave.isNotEmpty()) {
            userRepository.saveAll(usersToSave)
        }
    }

    private fun seedPosts() {
        if (postRepository.count() > 0) {
            return
        }

        postRepository.saveAll(
            samplePosts.map {
                Post(
                    title = it.title,
                    content = it.content,
                    username = it.username,
                    password = it.password
                )
            }
        )
    }

    private data class SeedUser(
        val username: String,
        val password: String
    )

    private data class SeedPost(
        val title: String,
        val content: String,
        val username: String,
        val password: String
    )

    companion object {
        private val sampleUsers = listOf(
            SeedUser(username = "test", password = "test"),
            SeedUser(username = "alice", password = "alice1234"),
            SeedUser(username = "bob", password = "bob1234"),
            SeedUser(username = "charlie", password = "charlie1234")
        )

        private val samplePosts = listOf(
            SeedPost(
                title = "Kotlin Spring Boot 시작하기",
                content = "Kotlin과 Spring Boot를 함께 사용할 때의 기본 프로젝트 구조를 정리했습니다.",
                username = "test",
                password = "test"
            ),
            SeedPost(
                title = "JPA 변경 감지 메모",
                content = "엔티티를 트랜잭션 안에서 수정하면 dirty checking으로 update가 반영됩니다.",
                username = "alice",
                password = "alice1234"
            ),
            SeedPost(
                title = "MockMvc로 컨트롤러 테스트하기",
                content = "WebMvcTest와 MockK를 조합해서 빠르게 API 응답 계약을 검증할 수 있습니다.",
                username = "bob",
                password = "bob1234"
            ),
            SeedPost(
                title = "Kotest 스타일 정리",
                content = "DescribeSpec과 BehaviorSpec은 테스트 맥락을 표현하는 방식이 달라 팀 합의가 중요합니다.",
                username = "charlie",
                password = "charlie1234"
            )
        )
    }
}
