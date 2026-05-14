package com.example.kotlinspringbootsample.domain.order.repository

import com.example.kotlinspringbootsample.domain.customer.Customer
import com.example.kotlinspringbootsample.domain.customer.repository.CustomerRepository
import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.ShippingAddress
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDateTime

@DataJpaTest
class OrderRepositoryTest(
    @Autowired private val orderRepository: OrderRepository,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val entityManagerFactory: EntityManagerFactory
) : DescribeSpec({

    lateinit var customer: Customer
    lateinit var createdOrder: Order

    beforeEach {
        customer = customerRepository.save(
            Customer(name = "order-customer", email = "order-customer@example.com")
        )

        createdOrder = orderRepository.save(
            Order(
                customer = customer,
                orderNo = "ORD-TEST-001",
                shippingAddress = ShippingAddress(
                    recipient = "Order User",
                    zipCode = "06236",
                    address1 = "Seoul Gangnam-daero 1",
                    address2 = "101-ho"
                )
            ).apply {
                replaceLines(
                    listOf(
                        OrderLineDraft("JPA Book", 2, BigDecimal("32000.00")),
                        OrderLineDraft("Hibernate Tips", 1, BigDecimal("45000.00"))
                    )
                )
            }
        )

        orderRepository.save(
            Order(
                customer = customer,
                orderNo = "ORD-TEST-002",
                shippingAddress = ShippingAddress(
                    recipient = "Paid User",
                    zipCode = "04147",
                    address1 = "Seoul Mapo-gu 77",
                    address2 = "201-ho"
                )
            ).apply {
                replaceLines(
                    listOf(
                        OrderLineDraft("Kotlin in Action", 1, BigDecimal("42000.00"))
                    )
                )
                markPaid(LocalDateTime.of(2026, 5, 8, 9, 0))
            }
        )
    }

    describe("OrderRepository") {
        it("customerName 파생 쿼리로 주문 목록을 조회할 수 있다") {
            val result = orderRepository.findAllByCustomerNameAndDeletedAtIsNull(
                "order-customer",
                PageRequest.of(0, 10)
            )

            result.totalElements shouldBe 2
            result.content.first().customer.name shouldBe "order-customer"
            result.content.first().totalAmount shouldBe BigDecimal("109000.00")
        }

        it("status 조건 파생 쿼리로 주문 목록을 조회할 수 있다") {
            entityManager.clear()

            val result = orderRepository.findAllByDeletedAtIsNullAndStatus(
                OrderStatus.PAID,
                PageRequest.of(0, 10)
            )

            result.totalElements shouldBe 1
            result.content.first().status shouldBe OrderStatus.PAID
            result.content.first().paidAt shouldBe LocalDateTime.of(2026, 5, 8, 9, 0)
        }

        it("상세 조회에서 customer와 orderLines를 함께 로드한다") {
            entityManager.clear()

            val result = orderRepository.findByIdAndDeletedAtIsNull(requireNotNull(createdOrder.id))

            result.shouldNotBeNull()
            result.lines.size shouldBe 2
            result.customer.name shouldBe "order-customer"

            val persistenceUnitUtil = entityManagerFactory.persistenceUnitUtil
            persistenceUnitUtil.isLoaded(result, "customer") shouldBe true
            persistenceUnitUtil.isLoaded(result, "orderLines") shouldBe true
        }

        it("fetch join 상세 조회도 customer와 orderLines를 함께 로드할 수 있다") {
            entityManager.clear()

            val result = orderRepository.findDetailByIdUsingFetchJoin(requireNotNull(createdOrder.id))

            result.shouldNotBeNull()
            result.lines.size shouldBe 2
            result.customer.name shouldBe "order-customer"

            val persistenceUnitUtil = entityManagerFactory.persistenceUnitUtil
            persistenceUnitUtil.isLoaded(result, "customer") shouldBe true
            persistenceUnitUtil.isLoaded(result, "orderLines") shouldBe true
        }

        it("JPQL 기본 조회와 EntityGraph 조회의 연관 로딩 차이를 확인할 수 있다") {
            entityManager.clear()

            val plainResult = orderRepository.findPageWithoutCustomer(PageRequest.of(0, 10))
            plainResult.content shouldHaveSize 2

            val persistenceUnitUtil = entityManagerFactory.persistenceUnitUtil
            persistenceUnitUtil.isLoaded(plainResult.content.first(), "customer") shouldBe false

            entityManager.clear()

            val entityGraphResult = orderRepository.findAllByDeletedAtIsNull(PageRequest.of(0, 10))
            entityGraphResult.content shouldHaveSize 2
            persistenceUnitUtil.isLoaded(entityGraphResult.content.first(), "customer") shouldBe true
        }

        it("EntityGraph와 fetch join은 상세 조회에서 같은 연관 로딩 결과를 만들 수 있다") {
            entityManager.clear()

            val entityGraphResult = orderRepository.findByIdAndDeletedAtIsNull(requireNotNull(createdOrder.id))
            entityManager.clear()
            val fetchJoinResult = orderRepository.findDetailByIdUsingFetchJoin(requireNotNull(createdOrder.id))

            entityGraphResult.shouldNotBeNull()
            fetchJoinResult.shouldNotBeNull()
            entityGraphResult.customer.name shouldBe fetchJoinResult.customer.name
            entityGraphResult.lines.size shouldBe fetchJoinResult.lines.size
            entityGraphResult.totalAmount shouldBe fetchJoinResult.totalAmount
        }

        it("projection 기반 JPQL로 상태별 주문 수를 집계할 수 있다") {
            entityManager.clear()

            val result = orderRepository.findStatusSummaries(null, null)
            val countByStatus = result.associate { it.status to it.count }

            countByStatus[OrderStatus.CREATED] shouldBe 1
            countByStatus[OrderStatus.PAID] shouldBe 1
        }

        it("projection 기반 JPQL 집계에 customerName, status 조건을 함께 적용할 수 있다") {
            entityManager.clear()

            val result = orderRepository.findStatusSummaries("order-customer", OrderStatus.PAID)

            result shouldHaveSize 1
            result.first().status shouldBe OrderStatus.PAID
            result.first().count shouldBe 1
        }

        it("동일한 customerName, status 조건을 파생 쿼리와 JPQL 조건식으로 같은 결과를 조회할 수 있다") {
            entityManager.clear()

            val pageable = PageRequest.of(0, 10)
            val derivedResult = orderRepository.findAllByCustomerNameAndStatusAndDeletedAtIsNull(
                "order-customer",
                OrderStatus.PAID,
                pageable
            )

            entityManager.clear()

            val jpqlResult = orderRepository.searchByConditions(
                "order-customer",
                OrderStatus.PAID,
                pageable
            )

            derivedResult.totalElements shouldBe jpqlResult.totalElements
            derivedResult.content.first().id shouldBe jpqlResult.content.first().id
            derivedResult.content.first().status shouldBe jpqlResult.content.first().status

            val persistenceUnitUtil = entityManagerFactory.persistenceUnitUtil
            persistenceUnitUtil.isLoaded(jpqlResult.content.first(), "customer") shouldBe true
        }
    }

    afterEach {
        orderRepository.deleteAll()
        customerRepository.deleteAll()
    }
})
