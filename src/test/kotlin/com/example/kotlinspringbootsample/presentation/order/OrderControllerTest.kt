package com.example.kotlinspringbootsample.presentation.order

import com.example.kotlinspringbootsample.application.order.OrderUseCase
import com.example.kotlinspringbootsample.application.order.command.CancelOrderCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrdersCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrderStatusSummariesCommand
import com.example.kotlinspringbootsample.application.order.command.OrderSearchMode
import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.application.order.command.ShipOrderCommand
import com.example.kotlinspringbootsample.application.order.result.OrderLineResult
import com.example.kotlinspringbootsample.application.order.result.OrderResult
import com.example.kotlinspringbootsample.application.order.result.OrderStatusSummaryResult
import com.example.kotlinspringbootsample.application.order.result.OrderSummaryResult
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.exception.InvalidOrderStatusTransitionException
import com.example.kotlinspringbootsample.infrastructure.security.CustomAuthenticationManager
import com.example.kotlinspringbootsample.infrastructure.security.JwtTokenProvider
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDateTime

@WebMvcTest(
    OrderController::class,
    excludeAutoConfiguration = [
        UserDetailsServiceAutoConfiguration::class,
        SecurityAutoConfiguration::class
    ]
)
class OrderControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @MockkBean private val orderUseCase: OrderUseCase,
    @MockkBean private val customAuthenticationManager: CustomAuthenticationManager,
    @MockkBean private val jwtTokenProvider: JwtTokenProvider
) : DescribeSpec({

    val orderedAt = LocalDateTime.of(2026, 5, 8, 11, 0)
    val paidAt = LocalDateTime.of(2026, 5, 8, 12, 5)
    val shippedAt = LocalDateTime.of(2026, 5, 8, 13, 0)

    describe("GET /api/orders/status-summary") {
        it("customerName, status 파라미터를 받아 projection 기반 상태 요약을 반환한다") {
            every {
                orderUseCase.getOrderStatusSummaries(
                    FindOrderStatusSummariesCommand(
                        customerName = "한수진",
                        status = OrderStatus.PAID
                    )
                )
            } returns listOf(
                OrderStatusSummaryResult(OrderStatus.PAID, 1)
            )

            mockMvc.get("/api/orders/status-summary") {
                param("customerName", "한수진")
                param("status", "PAID")
            }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.result") { value("success") }
                    jsonPath("$.data[0].status") { value("PAID") }
                    jsonPath("$.data[0].count") { value(1) }
                }

            verify {
                orderUseCase.getOrderStatusSummaries(
                    FindOrderStatusSummariesCommand(
                        customerName = "한수진",
                        status = OrderStatus.PAID
                    )
                )
            }
        }

        it("잘못된 status 파라미터면 sealed failure 포맷으로 400을 반환한다") {
            mockMvc.get("/api/orders/status-summary") {
                param("status", "WRONG")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.result") { value("failure") }
                jsonPath("$.code") { value("400") }
                jsonPath("$.message") { value("Invalid Request") }
                jsonPath("$.errors.status") { value("Failed to convert value to OrderStatus") }
            }
        }
    }

    describe("GET /api/orders") {
        it("status와 searchMode 파라미터를 받아 목록 조회를 수행한다") {
            every {
                orderUseCase.getOrders(
                    FindOrdersCommand(
                        page = 0,
                        size = 10,
                        customerName = "한수진",
                        status = OrderStatus.PAID,
                        searchMode = OrderSearchMode.JPQL
                    )
                )
            } returns PageImpl(
                listOf(
                    OrderSummaryResult(
                        id = 1L,
                        version = 2L,
                        orderNo = "ORD-2024-0001",
                        customerId = 1L,
                        customerName = "한수진",
                        status = OrderStatus.PAID,
                        totalAmount = BigDecimal("109000.00"),
                        orderedAt = orderedAt,
                        paidAt = paidAt,
                        shippedAt = null,
                        cancelledAt = null,
                        createdAt = orderedAt
                    )
                ),
                PageRequest.of(0, 10),
                1
            )

            mockMvc.get("/api/orders") {
                param("customerName", "한수진")
                param("status", "PAID")
                param("searchMode", "JPQL")
            }.andExpect {
                status { isOk() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.data.content[0].status") { value("PAID") }
                jsonPath("$.data.content[0].version") { value(2) }
                jsonPath("$.data.content[0].orderNo") { value("ORD-2024-0001") }
                jsonPath("$.data.content[0].customerName") { value("한수진") }
                jsonPath("$.data.content[0].paidAt") { value("2026-05-08T12:05:00") }
            }

            verify {
                orderUseCase.getOrders(
                    FindOrdersCommand(
                        page = 0,
                        size = 10,
                        customerName = "한수진",
                        status = OrderStatus.PAID,
                        searchMode = OrderSearchMode.JPQL
                    )
                )
            }
        }
    }

    describe("POST /api/orders/{id}/pay") {
        val idempotencyKey = "550e8400-e29b-41d4-a716-446655440000"

        it("Idempotency-Key 헤더와 함께 호출하면 결제 성공 응답을 반환한다") {
            every { orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey)) } returns sampleOrderResult(
                id = 1L,
                version = 3L,
                status = OrderStatus.PAID,
                orderedAt = orderedAt,
                paidAt = paidAt,
                paymentKey = "MOCK-PG-abc123"
            )

            mockMvc.post("/api/orders/1/pay") {
                header("Idempotency-Key", idempotencyKey)
            }.andExpect {
                status { isOk() }
                jsonPath("$.result") { value("success") }
                jsonPath("$.data.version") { value(3) }
                jsonPath("$.data.status") { value("PAID") }
                jsonPath("$.data.paidAt") { value("2026-05-08T12:05:00") }
                jsonPath("$.data.paymentKey") { value("MOCK-PG-abc123") }
            }

            verify { orderUseCase.payOrder(PayOrderCommand(1L, idempotencyKey)) }
        }

        it("Idempotency-Key 헤더가 누락되면 400 IDEMPOTENCY_KEY_REQUIRED를 반환한다") {
            mockMvc.post("/api/orders/1/pay").andExpect {
                status { isBadRequest() }
                jsonPath("$.result") { value("failure") }
                jsonPath("$.code") { value("IDEMPOTENCY_KEY_REQUIRED") }
            }
        }

        it("Idempotency-Key 헤더가 빈 문자열이면 400 IDEMPOTENCY_KEY_REQUIRED를 반환한다") {
            mockMvc.post("/api/orders/1/pay") {
                header("Idempotency-Key", "   ")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("IDEMPOTENCY_KEY_REQUIRED") }
            }
        }

        it("Idempotency-Key 헤더가 255자 초과면 400 IDEMPOTENCY_KEY_INVALID를 반환한다") {
            mockMvc.post("/api/orders/1/pay") {
                header("Idempotency-Key", "a".repeat(256))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("IDEMPOTENCY_KEY_INVALID") }
            }
        }
    }

    describe("POST /api/orders/{id}/ship") {
        it("배송 성공 시 shippedAt을 포함한 주문 응답을 반환한다") {
            every { orderUseCase.shipOrder(ShipOrderCommand(2L)) } returns sampleOrderResult(
                id = 2L,
                version = 4L,
                status = OrderStatus.SHIPPED,
                orderedAt = orderedAt,
                paidAt = paidAt,
                shippedAt = shippedAt
            )

            mockMvc.post("/api/orders/2/ship")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.result") { value("success") }
                    jsonPath("$.data.status") { value("SHIPPED") }
                    jsonPath("$.data.shippedAt") { value("2026-05-08T13:00:00") }
                }

            verify { orderUseCase.shipOrder(ShipOrderCommand(2L)) }
        }
    }

    describe("POST /api/orders/{id}/cancel") {
        it("상태 전이 규칙 위반이면 sealed failure 포맷으로 409를 반환한다") {
            val cancelKey = "550e8400-e29b-41d4-a716-446655440099"
            every { orderUseCase.cancelOrder(CancelOrderCommand(3L, cancelKey, null)) } throws
                InvalidOrderStatusTransitionException(
                    "only created or paid orders can be cancelled. current status: SHIPPED"
                )

            mockMvc.post("/api/orders/3/cancel") {
                header("Idempotency-Key", cancelKey)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.result") { value("failure") }
                jsonPath("$.code") { value("409") }
                jsonPath("$.message") { value("only created or paid orders can be cancelled. current status: SHIPPED") }
            }

            verify { orderUseCase.cancelOrder(CancelOrderCommand(3L, cancelKey, null)) }
        }
    }
})

private fun sampleOrderResult(
    id: Long,
    version: Long,
    status: OrderStatus,
    orderedAt: LocalDateTime,
    paidAt: LocalDateTime? = null,
    shippedAt: LocalDateTime? = null,
    cancelledAt: LocalDateTime? = null,
    paymentKey: String? = null
): OrderResult =
    OrderResult(
        id = id,
        version = version,
        orderNo = "ORD-2024-$id",
        customerId = 1L,
        customerName = "한수진",
        status = status,
        recipient = "한수진",
        zipCode = "06236",
        address1 = "Seoul Gangnam-daero 1",
        address2 = "101-ho",
        totalAmount = BigDecimal("109000.00"),
        items = listOf(
            OrderLineResult(
                productName = "JPA Book",
                quantity = 2,
                unitPrice = BigDecimal("32000.00"),
                lineAmount = BigDecimal("64000.00")
            )
        ),
        orderedAt = orderedAt,
        deliveryRequestedAt = null,
        paidAt = paidAt,
        shippedAt = shippedAt,
        cancelledAt = cancelledAt,
        trackingNumber = null,
        cancelReason = null,
        paymentKey = paymentKey,
        createdAt = orderedAt
    )
