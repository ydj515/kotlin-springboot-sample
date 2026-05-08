package com.example.kotlinspringbootsample.infrastructure.bootstrap

import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.ShippingAddress
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.post.Post
import com.example.kotlinspringbootsample.domain.post.repository.PostRepository
import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class InitializeDataLoader(
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
    private val postRepository: PostRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        seedUsers()
        seedOrders()
        seedPosts()
    }

    private fun seedUsers() {
        val usersToSave = sampleUsers
            .filterNot { userRepository.existsByUsername(it.username) }
            .map {
                User(
                    username = it.username,
                    password = passwordEncoder.encode(it.password)
                )
            }

        if (usersToSave.isNotEmpty()) {
            userRepository.saveAll(usersToSave)
        }
    }

    private fun seedPosts() {
        if (postRepository.count() > 0) {
            return
        }

        postRepository.saveAll(
            samplePosts.map {
                Post(
                    title = it.title,
                    content = it.content,
                    username = it.username,
                    password = it.password
                )
            }
        )
    }

    private fun seedOrders() {
        if (orderRepository.count() > 0) {
            return
        }

        orderRepository.saveAll(
            sampleOrders.mapNotNull { seed ->
                userRepository.findByUsername(seed.buyerUsername)
                    ?.let { buyer ->
                        Order(
                            buyer = buyer,
                            shippingAddress = ShippingAddress(
                                recipient = seed.recipient,
                                zipCode = seed.zipCode,
                                address1 = seed.address1,
                                address2 = seed.address2
                            )
                        ).apply {
                            replaceLines(
                                seed.items.map { item ->
                                    OrderLineDraft(
                                        productName = item.productName,
                                        quantity = item.quantity,
                                        unitPrice = item.unitPrice
                                    )
                                }
                            )
                            applyLifecycle(seed.status)
                        }
                    }
            }
        )
    }

    private fun Order.applyLifecycle(status: OrderStatus) = apply {
        val now = LocalDateTime.now()

        when (status) {
            OrderStatus.CREATED -> Unit
            OrderStatus.PAID -> markPaid(now.minusDays(2))
            OrderStatus.SHIPPED -> {
                markPaid(now.minusDays(3))
                markShipped(now.minusDays(1))
            }
            OrderStatus.CANCELLED -> {
                markPaid(now.minusDays(2))
                cancel(now.minusHours(18))
            }
        }
    }

    private data class SeedUser(
        val username: String,
        val password: String
    )

    private data class SeedPost(
        val title: String,
        val content: String,
        val username: String,
        val password: String
    )

    private data class SeedOrder(
        val buyerUsername: String,
        val recipient: String,
        val zipCode: String,
        val address1: String,
        val address2: String,
        val status: OrderStatus,
        val items: List<SeedOrderItem>
    )

    private data class SeedOrderItem(
        val productName: String,
        val quantity: Int,
        val unitPrice: BigDecimal
    )

    companion object {
        private val sampleUsers = listOf(
            SeedUser(username = "test", password = "test"),
            SeedUser(username = "alice", password = "alice1234"),
            SeedUser(username = "bob", password = "bob1234"),
            SeedUser(username = "charlie", password = "charlie1234")
        )

        private val samplePosts = listOf(
            SeedPost(
                title = "Kotlin Spring Boot 시작하기",
                content = "Kotlin과 Spring Boot를 함께 사용할 때의 기본 프로젝트 구조를 정리했습니다.",
                username = "test",
                password = "test"
            ),
            SeedPost(
                title = "JPA 변경 감지 메모",
                content = "엔티티를 트랜잭션 안에서 수정하면 dirty checking으로 update가 반영됩니다.",
                username = "alice",
                password = "alice1234"
            ),
            SeedPost(
                title = "MockMvc로 컨트롤러 테스트하기",
                content = "WebMvcTest와 MockK를 조합해서 빠르게 API 응답 계약을 검증할 수 있습니다.",
                username = "bob",
                password = "bob1234"
            ),
            SeedPost(
                title = "Kotest 스타일 정리",
                content = "DescribeSpec과 BehaviorSpec은 테스트 맥락을 표현하는 방식이 달라 팀 합의가 중요합니다.",
                username = "charlie",
                password = "charlie1234"
            )
        )

        private val sampleOrders = listOf(
            SeedOrder(
                buyerUsername = "alice",
                recipient = "Alice Kim",
                zipCode = "06236",
                address1 = "Seoul Gangnam-daero 123",
                address2 = "10F",
                status = OrderStatus.CREATED,
                items = listOf(
                    SeedOrderItem("Mechanical Keyboard", 1, BigDecimal("129000.00")),
                    SeedOrderItem("Wrist Rest", 1, BigDecimal("25000.00"))
                )
            ),
            SeedOrder(
                buyerUsername = "bob",
                recipient = "Bob Lee",
                zipCode = "04147",
                address1 = "Seoul Mapo-gu 77",
                address2 = "201-ho",
                status = OrderStatus.PAID,
                items = listOf(
                    SeedOrderItem("JPA Book", 2, BigDecimal("32000.00")),
                    SeedOrderItem("Notebook", 3, BigDecimal("4500.00"))
                )
            ),
            SeedOrder(
                buyerUsername = "charlie",
                recipient = "Charlie Park",
                zipCode = "13529",
                address1 = "Seongnam Bundang-gu 21",
                address2 = "502-ho",
                status = OrderStatus.SHIPPED,
                items = listOf(
                    SeedOrderItem("Spring in Action", 1, BigDecimal("38000.00")),
                    SeedOrderItem("Monitor Arm", 1, BigDecimal("59000.00"))
                )
            ),
            SeedOrder(
                buyerUsername = "test",
                recipient = "Test User",
                zipCode = "07328",
                address1 = "Seoul Yeongdeungpo-gu 9",
                address2 = "1203-ho",
                status = OrderStatus.CANCELLED,
                items = listOf(
                    SeedOrderItem("Wireless Mouse", 1, BigDecimal("49000.00"))
                )
            )
        )
    }
}
