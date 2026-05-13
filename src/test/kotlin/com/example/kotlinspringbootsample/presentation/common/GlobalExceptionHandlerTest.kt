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
import org.springframework.web.bind.annotation.RequestParam
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

        it("예상치 못한 예외는 내부 메시지를 노출하지 않고 공통 실패 응답을 반환한다") {
            mockMvc.get("/test/unexpected") {
                header("X-Trace-Id", "trace-test-002")
            }.andExpect {
                status { isInternalServerError() }
                jsonPath("$.result") { value("failure") }
                jsonPath("$.status") { value(500) }
                jsonPath("$.code") { value("500") }
                jsonPath("$.message") { value("Internal Server Error") }
                jsonPath("$.message") { value(org.hamcrest.Matchers.not("sensitive internal message")) }
                jsonPath("$.path") { value("/test/unexpected") }
                jsonPath("$.traceId") { value("trace-test-002") }
                jsonPath("$.timestamp") { exists() }
            }
        }

        it("타입 미스매치도 공통 필드 계약을 유지한다") {
            mockMvc.get("/test/type-mismatch") {
                header("X-Trace-Id", "trace-test-003")
                param("count", "not-a-number")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.result") { value("failure") }
                jsonPath("$.status") { value(400) }
                jsonPath("$.code") { value("400") }
                jsonPath("$.message") { value("Invalid Request") }
                jsonPath("$.path") { value("/test/type-mismatch") }
                jsonPath("$.traceId") { value("trace-test-003") }
                jsonPath("$.timestamp") { exists() }
                jsonPath("$.errors.count") { value("Failed to convert value to int") }
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

        @GetMapping("/test/unexpected")
        fun unexpected(): String {
            throw IllegalStateException("sensitive internal message")
        }

        @GetMapping("/test/type-mismatch")
        fun typeMismatch(@RequestParam count: Int): String = "count=$count"
    }
}
