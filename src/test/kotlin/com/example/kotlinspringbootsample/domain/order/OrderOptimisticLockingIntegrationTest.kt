package com.example.kotlinspringbootsample.domain.order

import com.example.kotlinspringbootsample.domain.order.repository.OrderRepository
import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
class OrderOptimisticLockingIntegrationTest @Autowired constructor(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    transactionManager: PlatformTransactionManager
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val createdOrderIds = mutableListOf<Long>()
    private val createdUserIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        createdOrderIds.forEach(orderRepository::deleteById)
        createdUserIds.forEach(userRepository::deleteById)
        createdOrderIds.clear()
        createdUserIds.clear()
    }

    @Test
    fun `서로 다른 트랜잭션의 오래된 주문 스냅샷을 저장하면 optimistic locking 예외가 발생한다`() {
        val createdOrderId = createOrder()

        val firstSnapshot = transactionTemplate.execute {
            orderRepository.findByIdAndDeletedAtIsNull(createdOrderId)
        }!!
        val secondSnapshot = transactionTemplate.execute {
            orderRepository.findByIdAndDeletedAtIsNull(createdOrderId)
        }!!

        firstSnapshot.markPaid(LocalDateTime.of(2026, 5, 8, 12, 0))
        transactionTemplate.executeWithoutResult {
            orderRepository.saveAndFlush(firstSnapshot)
        }

        secondSnapshot.cancel(LocalDateTime.of(2026, 5, 8, 12, 1))

        assertThrows(ObjectOptimisticLockingFailureException::class.java) {
            transactionTemplate.executeWithoutResult {
                orderRepository.saveAndFlush(secondSnapshot)
            }
        }

        val latestOrder = transactionTemplate.execute {
            orderRepository.findByIdAndDeletedAtIsNull(createdOrderId)
        }!!

        assertEquals(OrderStatus.PAID, latestOrder.status)
        assertEquals(1L, latestOrder.version)
        assertEquals(LocalDateTime.of(2026, 5, 8, 12, 0), latestOrder.paidAt)
        assertNull(latestOrder.cancelledAt)
    }

    private fun createOrder(): Long =
        transactionTemplate.execute {
            val suffix = System.nanoTime()
            val user = userRepository.save(
                User(
                    username = "optimistic-$suffix",
                    password = "encoded-password"
                )
            )
            createdUserIds += requireNotNull(user.id)

            orderRepository.saveAndFlush(
                Order(
                    buyer = user,
                    shippingAddress = ShippingAddress(
                        recipient = "Optimistic User",
                        zipCode = "06236",
                        address1 = "Seoul Gangnam-daero 1",
                        address2 = "101-ho"
                    )
                ).apply {
                    replaceLines(
                        listOf(
                            OrderLineDraft("Concurrency Book", 1, BigDecimal("33000.00"))
                        )
                    )
                }
            ).let { savedOrder ->
                requireNotNull(savedOrder.id).also(createdOrderIds::add)
            }
        }!!
}
