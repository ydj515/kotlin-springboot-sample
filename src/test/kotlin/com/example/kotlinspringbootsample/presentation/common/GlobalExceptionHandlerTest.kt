package com.example.kotlinspringbootsample.presentation.common

import com.example.kotlinspringbootsample.domain.user.exception.UserAlreadyException
import com.example.kotlinspringbootsample.infrastructure.security.CustomAuthenticationManager
import com.example.kotlinspringbootsample.infrastructure.security.TokenProvider
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(
    controllers = [GlobalExceptionHandlerTest.TestFailureController::class],
    excludeAutoConfiguration = [
        UserDetailsServiceAutoConfiguration::class,
        SecurityAutoConfiguration::class
    ]
)
@Import(GlobalExceptionHandler::class)
class GlobalExceptionHandlerTest(
    @Autowired private val mockMvc: MockMvc,
    @MockkBean private val customAuthenticationManager: CustomAuthenticationManager,
    @MockkBean private val tokenProvider: TokenProvider
) : DescribeSpec({

    describe("GlobalExceptionHandler") {
        it("비즈니스 예외를 공통 실패 응답 계약으로 변환한다") {
            mockMvc.get("/test/failure") {
                header("X-Trace-Id", "trace-test-001")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.result") { value("failure") }
                jsonPath("$.status") { value(409) }
                jsonPath("$.code") { value("USER_ALREADY_EXISTS") }
                jsonPath("$.message") { value("user already exists") }
                jsonPath("$.path") { value("/test/failure") }
                jsonPath("$.traceId") { value("trace-test-001") }
                jsonPath("$.timestamp") { exists() }
            }
        }
    }
}) {
    @RestController
    class TestFailureController {
        @GetMapping("/test/failure")
        fun failure(): String {
            throw UserAlreadyException()
        }
    }
}
