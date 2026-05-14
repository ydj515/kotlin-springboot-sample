package com.example.kotlinspringbootsample.application.order

import com.example.kotlinspringbootsample.application.order.command.CancelOrderCommand
import com.example.kotlinspringbootsample.application.order.command.PayOrderCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrdersCommand
import com.example.kotlinspringbootsample.application.order.command.FindOrderStatusSummariesCommand
import com.example.kotlinspringbootsample.application.order.command.OrderSearchMode
import com.example.kotlinspringbootsample.application.order.command.ShipOrderCommand
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.ShippingAddress
import com.example.kotlinspringbootsample.domain.order.exception.InvalidOrderStatusTransitionException
import com.example.kotlinspringbootsample.domain.order.policy.OrderItemPolicy
import com.example.kotlinspringbootsample.domain.order.policy.OrderStatusTransitionPolicy
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.order.repository.projection.OrderStatusSummaryProjection
import com.example.kotlinspringbootsample.domain.order.service.OrderLookupService
import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.service.UserLookupService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

class OrderUseCaseTest : BehaviorSpec({

    val orderRepository = mockk<OrderRepository>()
    val userLookupService = mockk<UserLookupService>()
    val orderLookupService = mockk<OrderLookupService>()
    val orderUseCase = OrderUseCase(
        orderRepository = orderRepository,
        userLookupService = userLookupService,
        orderLookupService = orderLookupService,
        orderItemPolicy = OrderItemPolicy(),
        orderStatusTransitionPolicy = OrderStatusTransitionPolicy()
    )

    beforeTest {
        clearMocks(orderRepository, userLookupService, orderLookupService)
    }

    Given("주문 결제 요청이 들어오면") {
        When("주문이 CREATED 상태면") {
            Then("lookup service로 주문을 보장하고 save 재호출 없이 PAID 상태와 결제 시각을 반영한다") {
                val order = sampleOrder(id = 1L)

                every { orderLookupService.requireById(1L) } returns order

                val result = orderUseCase.payOrder(PayOrderCommand(1L))

                result.status shouldBe OrderStatus.PAID
                result.paidAt.shouldNotBeNull()
                verify(exactly = 1) { orderLookupService.requireById(1L) }
                verify(exactly = 0) { orderRepository.save(any()) }
            }
        }

        When("이미 결제된 주문이면") {
            Then("상태 전이 예외를 던진다") {
                val order = sampleOrder(id = 1L).apply {
                    markPaid(LocalDateTime.of(2026, 5, 8, 10, 0))
                }

                every { orderLookupService.requireById(1L) } returns order

                val exception = shouldThrow<InvalidOrderStatusTransitionException> {
                    orderUseCase.payOrder(PayOrderCommand(1L))
                }

                exception.message shouldBe "only created orders can be paid. current status: PAID"
            }
        }
    }

    Given("주문 배송 요청이 들어오면") {
        When("주문이 PAID 상태면") {
            Then("SHIPPED 상태와 배송 시각을 반영한다") {
                val order = sampleOrder(id = 2L).apply {
                    markPaid(LocalDateTime.of(2026, 5, 7, 9, 0))
                }

                every { orderLookupService.requireById(2L) } returns order

                val result = orderUseCase.shipOrder(ShipOrderCommand(2L))

                result.status shouldBe OrderStatus.SHIPPED
                result.paidAt shouldBe LocalDateTime.of(2026, 5, 7, 9, 0)
                result.shippedAt.shouldNotBeNull()
                verify(exactly = 1) { orderLookupService.requireById(2L) }
            }
        }
    }

    Given("주문 취소 요청이 들어오면") {
        When("이미 배송된 주문이면") {
            Then("취소할 수 없다는 예외를 던진다") {
                val order = sampleOrder(id = 3L).apply {
                    markPaid(LocalDateTime.of(2026, 5, 6, 9, 0))
                    markShipped(LocalDateTime.of(2026, 5, 7, 14, 0))
                }

                every { orderLookupService.requireById(3L) } returns order

                val exception = shouldThrow<InvalidOrderStatusTransitionException> {
                    orderUseCase.cancelOrder(CancelOrderCommand(3L))
                }

                exception.message shouldBe "only created or paid orders can be cancelled. current status: SHIPPED"
                verify(exactly = 1) { orderLookupService.requireById(3L) }
            }
        }
    }

    Given("주문 목록 조회를 요청하면") {
        When("searchMode가 DERIVED이고 status 조건이 있으면") {
            Then("상태 조건용 파생 쿼리를 사용한다") {
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                val order = sampleOrder(id = 10L).apply {
                    markPaid(LocalDateTime.of(2026, 5, 8, 9, 0))
                }

                every {
                    orderRepository.findAllByDeletedAtIsNullAndStatus(OrderStatus.PAID, pageable)
                } returns PageImpl(listOf(order), pageable, 1)

                val result = orderUseCase.getOrders(
                    FindOrdersCommand(
                        page = 0,
                        size = 10,
                        status = OrderStatus.PAID,
                        searchMode = OrderSearchMode.DERIVED
                    )
                )

                result.content shouldHaveSize 1
                result.content.first().status shouldBe OrderStatus.PAID
                verify(exactly = 1) {
                    orderRepository.findAllByDeletedAtIsNullAndStatus(OrderStatus.PAID, pageable)
                }
            }
        }

        When("searchMode가 JPQL이고 buyerUsername, status 조건이 함께 있으면") {
            Then("nullable 조건식 기반 JPQL 조회를 사용한다") {
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                val order = sampleOrder(id = 11L).apply {
                    markPaid(LocalDateTime.of(2026, 5, 8, 9, 30))
                }

                every {
                    orderRepository.searchByConditions("buyer", OrderStatus.PAID, pageable)
                } returns PageImpl(listOf(order), pageable, 1)

                val result = orderUseCase.getOrders(
                    FindOrdersCommand(
                        page = 0,
                        size = 10,
                        buyerUsername = "buyer",
                        status = OrderStatus.PAID,
                        searchMode = OrderSearchMode.JPQL
                    )
                )

                result.content shouldHaveSize 1
                result.content.first().buyerUsername shouldBe "buyer"
                verify(exactly = 1) {
                    orderRepository.searchByConditions("buyer", OrderStatus.PAID, pageable)
                }
            }
        }
    }

    Given("주문 상태 요약을 조회하면") {
        When("buyerUsername, status 조건이 함께 있으면") {
            Then("필터가 적용된 projection 결과를 application result로 변환한다") {
                every { orderRepository.findStatusSummaries("buyer", OrderStatus.PAID) } returns listOf(
                    statusSummaryProjection(OrderStatus.PAID, 1)
                )

                val result = orderUseCase.getOrderStatusSummaries(
                    FindOrderStatusSummariesCommand(
                        buyerUsername = "buyer",
                        status = OrderStatus.PAID
                    )
                )

                result shouldHaveSize 1
                result[0].status shouldBe OrderStatus.PAID
                result[0].count shouldBe 1
                verify(exactly = 1) { orderRepository.findStatusSummaries("buyer", OrderStatus.PAID) }
            }
        }
    }
})

private fun sampleOrder(id: Long): Order =
    Order(
        id = id,
        buyer = User(username = "buyer", password = "encoded-password"),
        shippingAddress = ShippingAddress(
            recipient = "Buyer Kim",
            zipCode = "06236",
            address1 = "Seoul Gangnam-daero 1",
            address2 = "101-ho"
        )
    )

private fun statusSummaryProjection(
    orderStatus: OrderStatus,
    orderCount: Long
): OrderStatusSummaryProjection = object : OrderStatusSummaryProjection {
    override val status: OrderStatus = orderStatus
    override val count: Long = orderCount
}
