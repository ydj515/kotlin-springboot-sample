package com.example.kotlinspringbootsample.domain.outbox.repository

import com.example.kotlinspringbootsample.domain.outbox.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {
    /**
     * PENDING 상태이고 next_attempt_at이 도래한 이벤트를 limit만큼 잠금 후 반환.
     * MySQL 8+ / H2 2.x는 FOR UPDATE SKIP LOCKED 지원.
     * 동시 publisher 인스턴스가 같은 row를 잡지 않도록 보장.
     */
    @Query(
        value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
            ORDER BY id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findPendingForUpdate(
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<OutboxEvent>

    fun findByEventId(eventId: String): OutboxEvent?
}
