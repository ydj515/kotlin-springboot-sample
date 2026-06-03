package com.example.kotlinspringbootsample.domain.order.repository

import com.example.kotlinspringbootsample.domain.order.Order
import com.example.kotlinspringbootsample.domain.order.OrderStatus
import com.example.kotlinspringbootsample.domain.order.repository.projection.OrderStatusSummaryProjection
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderRepository : JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = ["customer"])
    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Order>

    @Query("select o from Order o where o.deletedAt is null")
    fun findPageWithoutCustomer(pageable: Pageable): Page<Order>

    @EntityGraph(attributePaths = ["customer"])
    fun findAllByCustomerNameAndDeletedAtIsNull(customerName: String, pageable: Pageable): Page<Order>

    @EntityGraph(attributePaths = ["customer"])
    fun findAllByDeletedAtIsNullAndStatus(status: OrderStatus, pageable: Pageable): Page<Order>

    @EntityGraph(attributePaths = ["customer"])
    fun findAllByCustomerNameAndStatusAndDeletedAtIsNull(
        customerName: String,
        status: OrderStatus,
        pageable: Pageable
    ): Page<Order>

    @EntityGraph(attributePaths = ["customer", "orderLines"])
    fun findByIdAndDeletedAtIsNull(id: Long): Order?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = ["customer", "orderLines"])
    @Query("select o from Order o where o.id = :id and o.deletedAt is null")
    fun findByIdAndDeletedAtIsNullForUpdate(@Param("id") id: Long): Order?

    @Query(
        """
        select distinct o
        from Order o
        join fetch o.customer
        left join fetch o.orderLines
        where o.id = :id
          and o.deletedAt is null
        """
    )
    fun findDetailByIdUsingFetchJoin(@Param("id") id: Long): Order?

    @EntityGraph(attributePaths = ["customer"])
    @Query(
        """
        select o
        from Order o
        join o.customer c
        where o.deletedAt is null
          and (:customerName is null or c.name = :customerName)
          and (:status is null or o.status = :status)
        """
    )
    fun searchByConditions(
        @Param("customerName") customerName: String?,
        @Param("status") status: OrderStatus?,
        pageable: Pageable
    ): Page<Order>

    @Query(
        """
        select o.status as status, count(o) as count
        from Order o
        join o.customer c
        where o.deletedAt is null
          and (:customerName is null or c.name = :customerName)
          and (:status is null or o.status = :status)
        group by o.status
        order by o.status
        """
    )
    fun findStatusSummaries(
        @Param("customerName") customerName: String?,
        @Param("status") status: OrderStatus?
    ): List<OrderStatusSummaryProjection>
}
