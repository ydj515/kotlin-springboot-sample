package com.example.kotlinspringbootsample.presentation.user

import com.example.kotlinspringbootsample.application.user.UserUseCase
import com.example.kotlinspringbootsample.application.user.command.SignupCommand
import com.example.kotlinspringbootsample.application.user.result.SignupResult
import com.example.kotlinspringbootsample.config.logging.MdcLoggingFilter
import com.example.kotlinspringbootsample.presentation.user.request.SignupRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.verify
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.not
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post

@WebMvcTest(
    UserController::class,
    excludeAutoConfiguration = [
        UserDetailsServiceAutoConfiguration::class,
        SecurityAutoConfiguration::class
    ]
)
@Import(MdcLoggingFilter::class)
class UserControllerTest(
    @Autowired private val mockMvc: org.springframework.test.web.servlet.MockMvc,
    @MockkBean private val userUseCase: UserUseCase
) : DescribeSpec({

    val mapper = jacksonObjectMapper()

    describe("POST /api/users") {
        it("회원가입 성공 시 sealed success 포맷으로 201을 반환한다") {
            val request = SignupRequest("user1", "password1")
            val command = SignupCommand("user1", "password1")
            val response = SignupResult("user1")
            every { userUseCase.registerMember(command) } returns response

            mockMvc.post("/api/users") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.code") { value("201") }
                jsonPath("$.data.username") { value("user1") }
            }

            verify { userUseCase.registerMember(command) }
        }

        it("유효성 검증 실패 시 sealed failure 포맷으로 400을 반환한다") {
            val invalidRequest = SignupRequest("ab", "x")

            mockMvc.post("/api/users") {
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

        it("X-Request-Id를 유지하고 X-Trace-Id를 응답 헤더에 포함한다") {
            val request = SignupRequest("user1", "password1")
            val command = SignupCommand("user1", "password1")
            val response = SignupResult("user1")
            val requestId = "request-id-001"
            every { userUseCase.registerMember(command) } returns response

            mockMvc.post("/api/users") {
                header("X-Request-Id", requestId)
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                header { string("X-Request-Id", requestId) }
                header { string("X-Trace-Id", matchesPattern("[A-Za-z0-9._-]{1,128}")) }
            }

            verify { userUseCase.registerMember(command) }
        }

        it("허용되지 않은 request/trace header는 안전한 새 값으로 대체한다") {
            val request = SignupRequest("user1", "password1")
            val command = SignupCommand("user1", "password1")
            val response = SignupResult("user1")
            val invalidRequestId = "bad request id"
            val invalidTraceId = "trace\npoison"
            every { userUseCase.registerMember(command) } returns response

            mockMvc.post("/api/users") {
                header("X-Request-Id", invalidRequestId)
                header("X-Trace-Id", invalidTraceId)
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                header {
                    string(
                        "X-Request-Id",
                        allOf(
                            matchesPattern("[A-Za-z0-9._-]{1,128}"),
                            not(equalTo(invalidRequestId))
                        )
                    )
                }
                header {
                    string(
                        "X-Trace-Id",
                        allOf(
                            matchesPattern("[A-Za-z0-9._-]{1,128}"),
                            not(equalTo(invalidTraceId))
                        )
                    )
                }
            }

            verify { userUseCase.registerMember(command) }
        }
    }
})
