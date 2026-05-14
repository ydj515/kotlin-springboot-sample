package com.example.kotlinspringbootsample.infrastructure.bootstrap

import com.example.kotlinspringbootsample.domain.customer.Customer
import com.example.kotlinspringbootsample.domain.customer.repository.CustomerRepository
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
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        val rolesByName = seedRoles()
        seedUsers(rolesByName)
        val customersByName = seedCustomers()
        seedOrders(customersByName)
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

    private fun seedCustomers(): Map<String, Customer> {
        sampleCustomers.forEach { seed ->
            if (customerRepository.findByName(seed.name) == null) {
                customerRepository.save(Customer(name = seed.name, email = seed.email))
            }
        }
        return customerRepository.findAll().associateBy { it.name }
    }

    private fun seedOrders(customersByName: Map<String, Customer>) {
        if (orderRepository.count() > 0) {
            return
        }

        sampleOrders.forEach { seed ->
            val customer = customersByName[seed.customerName] ?: return@forEach
            val order = Order(
                customer = customer,
                orderNo = seed.orderNo,
                shippingAddress = ShippingAddress(
                    recipient = seed.recipient,
                    zipCode = seed.zipCode,
                    address1 = seed.address1,
                    address2 = seed.address2
                ),
                orderedAt = seed.orderedAt,
                deliveryRequestedAt = seed.deliveryRequestedAt
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
                applyLifecycle(seed)
            }
            orderRepository.save(order)
        }
    }

    private fun Order.applyLifecycle(seed: SeedOrder) = apply {
        when (seed.status) {
            OrderStatus.CREATED -> Unit
            OrderStatus.PAID -> markPaid(seed.paidAt ?: LocalDateTime.now())
            OrderStatus.SHIPPED -> {
                markPaid(seed.paidAt ?: LocalDateTime.now())
                markShipped(seed.shippedAt ?: LocalDateTime.now(), seed.trackingNumber)
            }
            OrderStatus.CANCELLED -> cancel(seed.cancelledAt ?: LocalDateTime.now(), seed.cancelReason)
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

    private data class SeedCustomer(
        val name: String,
        val email: String
    )

    private data class SeedOrder(
        val customerName: String,
        val orderNo: String,
        val recipient: String,
        val zipCode: String,
        val address1: String,
        val address2: String,
        val status: OrderStatus,
        val orderedAt: LocalDateTime,
        val deliveryRequestedAt: LocalDateTime? = null,
        val paidAt: LocalDateTime? = null,
        val shippedAt: LocalDateTime? = null,
        val cancelledAt: LocalDateTime? = null,
        val trackingNumber: String? = null,
        val cancelReason: String? = null,
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

        private val sampleCustomers = listOf(
            SeedCustomer(name = "한수진", email = "sujin.han@example.com"),
            SeedCustomer(name = "강민호", email = "minho.kang@example.com"),
            SeedCustomer(name = "최아라", email = "ara.choi@example.com")
        )

        private val sampleOrders = listOf(
            SeedOrder(
                customerName = "한수진",
                orderNo = "ORD-2024-0001",
                recipient = "한수진",
                zipCode = "06236",
                address1 = "Seoul Gangnam-daero 123",
                address2 = "10F",
                status = OrderStatus.CREATED,
                orderedAt = LocalDateTime.of(2024, 7, 1, 9, 30, 0),
                deliveryRequestedAt = LocalDateTime.of(2024, 7, 3, 18, 0, 0),
                items = listOf(
                    SeedOrderItem("Mechanical Keyboard", 1, BigDecimal("129000.00")),
                    SeedOrderItem("Wrist Rest", 1, BigDecimal("40000.00"))
                )
            ),
            SeedOrder(
                customerName = "한수진",
                orderNo = "ORD-2024-0002",
                recipient = "한수진",
                zipCode = "06236",
                address1 = "Seoul Gangnam-daero 123",
                address2 = "10F",
                status = OrderStatus.PAID,
                orderedAt = LocalDateTime.of(2024, 7, 5, 14, 10, 0),
                deliveryRequestedAt = LocalDateTime.of(2024, 7, 6, 18, 0, 0),
                paidAt = LocalDateTime.of(2024, 7, 5, 14, 20, 0),
                items = listOf(
                    SeedOrderItem("Wireless Mouse", 1, BigDecimal("52000.00")),
                    SeedOrderItem("Desk Mat", 1, BigDecimal("35000.00"))
                )
            ),
            SeedOrder(
                customerName = "강민호",
                orderNo = "ORD-2024-0003",
                recipient = "강민호",
                zipCode = "04147",
                address1 = "Seoul Mapo-gu 77",
                address2 = "201-ho",
                status = OrderStatus.SHIPPED,
                orderedAt = LocalDateTime.of(2024, 7, 7, 10, 0, 0),
                deliveryRequestedAt = LocalDateTime.of(2024, 7, 8, 12, 0, 0),
                paidAt = LocalDateTime.of(2024, 7, 7, 10, 5, 0),
                shippedAt = LocalDateTime.of(2024, 7, 7, 13, 0, 0),
                trackingNumber = "TRACK-2024-0003",
                items = listOf(
                    SeedOrderItem("27-inch Monitor", 1, BigDecimal("219000.00")),
                    SeedOrderItem("HDMI Cable", 2, BigDecimal("12000.00"))
                )
            ),
            SeedOrder(
                customerName = "최아라",
                orderNo = "ORD-2024-0004",
                recipient = "최아라",
                zipCode = "13529",
                address1 = "Seongnam Bundang-gu 21",
                address2 = "502-ho",
                status = OrderStatus.CANCELLED,
                orderedAt = LocalDateTime.of(2024, 7, 8, 16, 20, 0),
                cancelledAt = LocalDateTime.of(2024, 7, 8, 16, 25, 0),
                cancelReason = "결제 검증 실패로 주문이 취소되었습니다.",
                items = listOf(
                    SeedOrderItem("Web Camera", 1, BigDecimal("59000.00"))
                )
            )
        )
    }
}
