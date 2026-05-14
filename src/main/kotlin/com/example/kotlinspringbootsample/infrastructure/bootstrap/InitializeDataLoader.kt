package com.example.kotlinspringbootsample.infrastructure.bootstrap

import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderLineDraft
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.ShippingAddress
import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.user.Role
import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.UserType
import com.example.kotlinspringbootsample.domain.user.repository.RoleRepository
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class InitializeDataLoader(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val orderRepository: OrderRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        val rolesByName = seedRoles()
        seedUsers(rolesByName)
        seedOrders()
    }

    private fun seedRoles(): Map<String, Role> {
        sampleRoles.forEach { seed ->
            if (roleRepository.findByName(seed.name) == null) {
                roleRepository.save(Role(name = seed.name, description = seed.description))
            }
        }
        return roleRepository.findAll().associateBy { it.name }
    }

    private fun seedUsers(rolesByName: Map<String, Role>) {
        sampleUsers
            .filterNot { userRepository.existsByUsername(it.username) }
            .forEach { seed ->
                userRepository.save(
                    User(
                        username = seed.username,
                        password = passwordEncoder.encode(seed.rawPassword),
                        name = seed.name,
                        email = seed.email,
                        lastLoginAt = seed.lastLoginAt,
                        lastPasswordUpdatedAt = seed.lastPasswordUpdatedAt,
                        userType = seed.userType,
                        trialCount = seed.trialCount,
                        roles = seed.roleNames
                            .mapNotNull(rolesByName::get)
                            .toMutableSet()
                    )
                )
            }
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
        when (status) {
            OrderStatus.CREATED -> Unit
            OrderStatus.PAID -> markPaid(LocalDateTime.of(2024, 7, 5, 14, 20, 0))
            OrderStatus.SHIPPED -> {
                markPaid(LocalDateTime.of(2024, 7, 7, 10, 5, 0))
                markShipped(LocalDateTime.of(2024, 7, 7, 13, 0, 0))
            }
            OrderStatus.CANCELLED -> cancel(LocalDateTime.of(2024, 7, 8, 16, 25, 0))
        }
    }

    private data class SeedRole(
        val name: String,
        val description: String
    )

    private data class SeedUser(
        val username: String,
        val rawPassword: String,
        val name: String,
        val email: String,
        val userType: UserType,
        val trialCount: Int,
        val lastLoginAt: LocalDateTime,
        val lastPasswordUpdatedAt: LocalDateTime,
        val roleNames: List<String>
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
        private val sampleRoles = listOf(
            SeedRole("ADMIN", "admin"),
            SeedRole("PLAIN", "plain"),
            SeedRole("AAA", "aaa"),
            SeedRole("BBB", "bbb")
        )

        private val sampleUsers = listOf(
            SeedUser(
                username = "user123",
                rawPassword = "password123",
                name = "John Doe",
                email = "john.doe@example.com",
                userType = UserType.USER,
                trialCount = 1,
                lastLoginAt = LocalDateTime.of(2024, 6, 1, 10, 30, 0),
                lastPasswordUpdatedAt = LocalDateTime.of(2024, 5, 1, 9, 0, 0),
                roleNames = listOf("ADMIN", "PLAIN")
            ),
            SeedUser(
                username = "user456",
                rawPassword = "password456",
                name = "Jane Smith",
                email = "jane.smith@example.com",
                userType = UserType.MANAGER,
                trialCount = 2,
                lastLoginAt = LocalDateTime.of(2024, 6, 10, 14, 45, 0),
                lastPasswordUpdatedAt = LocalDateTime.of(2024, 5, 15, 11, 15, 0),
                roleNames = listOf("ADMIN", "AAA", "BBB")
            )
        )

        private val sampleOrders = listOf(
            SeedOrder(
                buyerUsername = "user123",
                recipient = "한수진",
                zipCode = "06236",
                address1 = "Seoul Gangnam-daero 123",
                address2 = "10F",
                status = OrderStatus.CREATED,
                items = listOf(
                    SeedOrderItem("Mechanical Keyboard", 1, BigDecimal("129000.00")),
                    SeedOrderItem("Wrist Rest", 1, BigDecimal("40000.00"))
                )
            ),
            SeedOrder(
                buyerUsername = "user123",
                recipient = "한수진",
                zipCode = "06236",
                address1 = "Seoul Gangnam-daero 123",
                address2 = "10F",
                status = OrderStatus.PAID,
                items = listOf(
                    SeedOrderItem("Wireless Mouse", 1, BigDecimal("52000.00")),
                    SeedOrderItem("Desk Mat", 1, BigDecimal("35000.00"))
                )
            ),
            SeedOrder(
                buyerUsername = "user456",
                recipient = "강민호",
                zipCode = "04147",
                address1 = "Seoul Mapo-gu 77",
                address2 = "201-ho",
                status = OrderStatus.SHIPPED,
                items = listOf(
                    SeedOrderItem("27-inch Monitor", 1, BigDecimal("219000.00")),
                    SeedOrderItem("HDMI Cable", 2, BigDecimal("12000.00"))
                )
            ),
            SeedOrder(
                buyerUsername = "user456",
                recipient = "최아라",
                zipCode = "13529",
                address1 = "Seongnam Bundang-gu 21",
                address2 = "502-ho",
                status = OrderStatus.CANCELLED,
                items = listOf(
                    SeedOrderItem("Web Camera", 1, BigDecimal("59000.00"))
                )
            )
        )
    }
}
