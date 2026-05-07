package com.example.kotlinspringbootsample.user.controller

import com.example.kotlinspringbootsample.config.security.CustomAuthenticationManager
import com.example.kotlinspringbootsample.config.security.TokenProvider
import com.example.kotlinspringbootsample.user.dto.SignupRequest
import com.example.kotlinspringbootsample.user.dto.SignupResponse
import com.example.kotlinspringbootsample.user.service.UserService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post

@WebMvcTest(
    UserController::class,
    excludeAutoConfiguration = [
        UserDetailsServiceAutoConfiguration::class,
        SecurityAutoConfiguration::class
    ]
)
class UserControllerTest(
    @Autowired private val mockMvc: org.springframework.test.web.servlet.MockMvc,
    @MockkBean private val userService: UserService,
    @MockkBean private val customAuthenticationManager: CustomAuthenticationManager,
    @MockkBean private val tokenProvider: TokenProvider
) : DescribeSpec({

    val mapper = jacksonObjectMapper()

    describe("POST /signup") {
        it("회원가입 성공 시 sealed success 포맷으로 201을 반환한다") {
            val request = SignupRequest("user1", "password1")
            val response = SignupResponse("user1")
            every { userService.registerMember(request) } returns response

            mockMvc.post("/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.code") { value("201") }
                jsonPath("$.data.username") { value("user1") }
            }

            verify { userService.registerMember(request) }
        }

        it("유효성 검증 실패 시 sealed failure 포맷으로 400을 반환한다") {
            val invalidRequest = SignupRequest("ab", "x")

            mockMvc.post("/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(invalidRequest)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.result") { value("failure") }
                jsonPath("$.code") { value("400") }
                jsonPath("$.errors.username") { value("Username must be between 4 and 10 characters") }
                jsonPath("$.errors.password") { value("Password must be between 8 and 15 characters") }
            }
        }
    }
})
