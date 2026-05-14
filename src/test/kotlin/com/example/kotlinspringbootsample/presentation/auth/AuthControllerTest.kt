package com.example.kotlinspringbootsample.presentation.auth

import com.example.kotlinspringbootsample.application.auth.AuthUseCase
import com.example.kotlinspringbootsample.application.auth.command.LoginCommand
import com.example.kotlinspringbootsample.application.auth.result.LoginResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post

@WebMvcTest(
    AuthController::class,
    excludeAutoConfiguration = [
        UserDetailsServiceAutoConfiguration::class,
        SecurityAutoConfiguration::class
    ]
)
class AuthControllerTest(
    @Autowired private val mockMvc: org.springframework.test.web.servlet.MockMvc,
    @MockkBean private val authUseCase: AuthUseCase
) : DescribeSpec({

    val mapper = jacksonObjectMapper()

    describe("POST /api/auth/login") {
        it("로그인 성공 시 Authorization 헤더와 accessToken payload를 반환한다") {
            val command = LoginCommand("alice", "secret123")
            every { authUseCase.login(command) } returns LoginResult(
                username = "alice",
                tokenType = "Bearer",
                accessToken = "jwt-token",
                accessTokenExpiresAt = 1893456000000
            )

            mockMvc.post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(mapOf("username" to "alice", "password" to "secret123"))
            }.andExpect {
                status { isOk() }
                header { string("Authorization", "Bearer jwt-token") }
                jsonPath("$.data.username") { value("alice") }
                jsonPath("$.data.accessToken") { value("jwt-token") }
            }
        }
    }
})
