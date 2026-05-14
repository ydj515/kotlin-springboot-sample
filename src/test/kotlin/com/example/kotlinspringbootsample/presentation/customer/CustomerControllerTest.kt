package com.example.kotlinspringbootsample.presentation.customer

import com.example.kotlinspringbootsample.application.customer.CustomerUseCase
import com.example.kotlinspringbootsample.application.customer.command.FindCustomersCommand
import com.example.kotlinspringbootsample.application.customer.command.GetCustomerCommand
import com.example.kotlinspringbootsample.application.customer.result.CustomerResult
import com.example.kotlinspringbootsample.config.logging.MdcLoggingFilter
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(
    CustomerController::class,
    excludeAutoConfiguration = [
        UserDetailsServiceAutoConfiguration::class,
        SecurityAutoConfiguration::class
    ]
)
@Import(MdcLoggingFilter::class)
class CustomerControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @MockkBean private val customerUseCase: CustomerUseCase
) : DescribeSpec({

    fun sample(id: Long, name: String, email: String): CustomerResult = CustomerResult(
        id = id,
        name = name,
        email = email,
        createdAt = null,
        updatedAt = null
    )

    describe("GET /api/customers") {
        it("전체 고객 목록을 sealed success 포맷으로 반환한다") {
            every { customerUseCase.findAll(any<FindCustomersCommand>()) } returns listOf(
                sample(1L, "한수진", "sujin.han@example.com"),
                sample(2L, "강민호", "minho.kang@example.com")
            )

            mockMvc.get("/api/customers").andExpect {
                status { isOk() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.data.length()") { value(2) }
                jsonPath("$.data[0].name") { value("한수진") }
                jsonPath("$.data[1].name") { value("강민호") }
            }

            verify { customerUseCase.findAll(any<FindCustomersCommand>()) }
        }
    }

    describe("GET /api/customers/{id}") {
        it("id로 단건을 sealed success 포맷으로 반환한다") {
            every { customerUseCase.findById(GetCustomerCommand(3L)) } returns
                sample(3L, "최아라", "ara.choi@example.com")

            mockMvc.get("/api/customers/3").andExpect {
                status { isOk() }
                jsonPath("$.data.id") { value(3) }
                jsonPath("$.data.name") { value("최아라") }
                jsonPath("$.data.email") { value("ara.choi@example.com") }
            }
        }
    }
})
