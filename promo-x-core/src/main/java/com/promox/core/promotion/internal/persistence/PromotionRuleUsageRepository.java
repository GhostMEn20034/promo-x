package com.promox.core.promotion.internal.persistence;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionRuleUsageRepository extends JpaRepository<PromotionRuleUsage, UUID> {
    // Row lock so concurrent confirm/cancel calls on one usage can't both act on the same status.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PromotionRuleUsage> findWithLockById(UUID id);

    /**
     * Usages still in the given status whose deadline has passed, oldest first. The rows are
     * locked, so a concurrent confirm/cancel or a second application instance running the same
     * job waits, then sees the new status and leaves the usage alone.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u FROM PromotionRuleUsage u
        WHERE u.status = :status AND u.expiresAt <= :now
        ORDER BY u.expiresAt
    """)
    List<PromotionRuleUsage> findOverdueWithLock(
        @Param("status") PromotionRuleUsageStatus status,
        @Param("now") Instant now,
        Pageable pageable
    );
}
