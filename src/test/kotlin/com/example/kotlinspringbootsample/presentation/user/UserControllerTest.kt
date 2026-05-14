package com.example.kotlinspringbootsample.presentation.user

import com.example.kotlinspringbootsample.application.user.UserUseCase
import com.example.kotlinspringbootsample.application.user.command.CreateUserCommand
import com.example.kotlinspringbootsample.application.user.command.DeleteUserCommand
import com.example.kotlinspringbootsample.application.user.command.FindUsersCommand
import com.example.kotlinspringbootsample.application.user.command.GetUserByUsernameCommand
import com.example.kotlinspringbootsample.application.user.command.GetUserCommand
import com.example.kotlinspringbootsample.application.user.command.UpdateUserCommand
import com.example.kotlinspringbootsample.application.user.result.DeleteUserResult
import com.example.kotlinspringbootsample.application.user.result.UpdateUserResult
import com.example.kotlinspringbootsample.application.user.result.UserResult
import com.example.kotlinspringbootsample.config.logging.MdcLoggingFilter
import com.example.kotlinspringbootsample.domain.user.UserType
import com.example.kotlinspringbootsample.presentation.user.request.CreateUserRequest
import com.example.kotlinspringbootsample.presentation.user.request.UpdateUserRequest
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@WebMvcTest(
    UserController::class,
    excludeAutoConfiguration = [
        UserDetailsServiceAutoConfiguration::class,
        SecurityAutoConfiguration::class
    ]
)
@Import(MdcLoggingFilter::class)
class UserControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @MockkBean private val userUseCase: UserUseCase
) : DescribeSpec({

    val mapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun sampleUserResult(
        id: Long = 1L,
        username: String = "user1",
        name: String? = "User One",
        email: String? = "user1@example.com",
        userType: UserType? = UserType.USER
    ): UserResult = UserResult(
        id = id,
        username = username,
        name = name,
        email = email,
        userType = userType?.name,
        lastLoginAt = null,
        createdAt = null,
        updatedAt = null,
        deletedAt = null,
        lastPasswordUpdatedAt = null,
        trialCount = 0,
        roles = emptyList()
    )

    describe("POST /api/users") {
        it("회원가입 성공 시 sealed success 포맷으로 201을 반환한다") {
            val request = CreateUserRequest(
                username = "user1",
                password = "password1",
                name = "User One",
                email = "user1@example.com",
                userType = UserType.USER,
                trialCount = 0
            )
            every { userUseCase.create(request.toCommand()) } returns sampleUserResult()

            mockMvc.post("/api/users") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.code") { value("201") }
                jsonPath("$.data.id") { value(1) }
                jsonPath("$.data.username") { value("user1") }
                jsonPath("$.data.userType") { value("USER") }
                jsonPath("$.data.roles") { isEmpty() }
            }

            verify { userUseCase.create(request.toCommand()) }
        }

        it("유효성 검증 실패 시 sealed failure 포맷으로 400을 반환한다") {
            val invalidRequest = CreateUserRequest(username = "ab", password = "x")

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
            val request = CreateUserRequest(username = "user1", password = "password1")
            val requestId = "request-id-001"
            every { userUseCase.create(any<CreateUserCommand>()) } returns sampleUserResult()

            mockMvc.post("/api/users") {
                header("X-Request-Id", requestId)
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                header { string("X-Request-Id", requestId) }
                header { string("X-Trace-Id", matchesPattern("[A-Za-z0-9._-]{1,128}")) }
            }
        }

        it("허용되지 않은 request/trace header는 안전한 새 값으로 대체한다") {
            val request = CreateUserRequest(username = "user1", password = "password1")
            val invalidRequestId = "bad request id"
            val invalidTraceId = "trace\npoison"
            every { userUseCase.create(any<CreateUserCommand>()) } returns sampleUserResult()

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
        }
    }

    describe("GET /api/users") {
        it("전체 사용자 목록을 sealed success 포맷으로 반환한다") {
            every { userUseCase.findAll(any<FindUsersCommand>()) } returns listOf(
                sampleUserResult(id = 1L, username = "user1"),
                sampleUserResult(id = 2L, username = "user2", name = "User Two", email = "user2@example.com")
            )

            mockMvc.get("/api/users").andExpect {
                status { isOk() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.data.length()") { value(2) }
                jsonPath("$.data[0].username") { value("user1") }
                jsonPath("$.data[1].username") { value("user2") }
            }
        }
    }

    describe("GET /api/users/{id}") {
        it("id로 사용자 단건을 반환한다") {
            every { userUseCase.findById(GetUserCommand(7L)) } returns sampleUserResult(id = 7L, username = "lookup-user")

            mockMvc.get("/api/users/7").andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(7) }
                jsonPath("$.data.username") { value("lookup-user") }
            }
        }
    }

    describe("GET /api/users/detail") {
        it("쿼리 파라미터 username으로 사용자를 조회한다") {
            every { userUseCase.findByUsername(GetUserByUsernameCommand("alice")) } returns
                sampleUserResult(id = 9L, username = "alice")

            mockMvc.get("/api/users/detail") {
                param("username", "alice")
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(9) }
                jsonPath("$.data.username") { value("alice") }
            }
        }
    }

    describe("PUT /api/users") {
        it("사용자 수정 결과로 id와 updatedCount를 반환한다") {
            val request = UpdateUserRequest(
                id = 3L,
                username = "user3",
                password = "newpass1",
                name = "User Three",
                email = "user3@example.com",
                userType = UserType.MANAGER,
                trialCount = 1
            )
            every { userUseCase.update(any<UpdateUserCommand>()) } returns UpdateUserResult(id = 3L, updatedCount = 1)

            mockMvc.put("/api/users") {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(3) }
                jsonPath("$.data.updatedCount") { value(1) }
            }
        }
    }

    describe("DELETE /api/users/{id}") {
        it("삭제 결과로 id와 deletedCount를 반환한다") {
            every { userUseCase.delete(DeleteUserCommand(5L)) } returns DeleteUserResult(id = 5L, deletedCount = 1)

            mockMvc.delete("/api/users/5").andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(5) }
                jsonPath("$.data.deletedCount") { value(1) }
            }
        }
    }
})
