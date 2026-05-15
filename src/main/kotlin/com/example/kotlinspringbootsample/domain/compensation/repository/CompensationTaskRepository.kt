package com.example.kotlinspringbootsample.domain.compensation.repository

import com.example.kotlinspringbootsample.domain.compensation.CompensationTask
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CompensationTaskRepository : JpaRepository<CompensationTask, Long> {
    /**
     * PENDING 상태이고 next_attempt_at이 도래한 보상 task를 limit만큼 잠금 후 반환.
     * MySQL 8+에서 동시 worker 인스턴스가 같은 row를 잡지 않도록 보장 (SKIP LOCKED).
     * H2 2.x는 syntax만 통과 — 실제 동시성 검증은 MySQL 통합 테스트에서만.
     */
    @Query(
        value = """
            SELECT * FROM compensation_tasks
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
    ): List<CompensationTask>
}
