package com.promox.core.promotion.internal.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<PromotionRule, Long>, JpaSpecificationExecutor<PromotionRule> {
    @EntityGraph(attributePaths = {"productExternalIds", "categoryExternalIds"})
    List<PromotionRule> findAllWithProductsBy();

    @EntityGraph(attributePaths = {"productExternalIds", "categoryExternalIds"})
    Optional<PromotionRule> findWithProductsById(Long id);

    @Query("""
        SELECT new com.promox.core.promotion.internal.persistence.PromotionRuleBaseProjection(
            p.id, p.storefrontId, p.name, p.priority, p.isShortCircuit, p.hasUsageLimit, p.conditionTree, p.action
        )
        FROM PromotionRule p
        WHERE p.storefrontId = :storefrontId AND p.isActive = true
        ORDER BY p.priority DESC
    """)
    List<PromotionRuleBaseProjection> findAllActiveProjectionsByStorefrontIdOrderByPriorityDesc(
        @Param("storefrontId") Long storefrontId
    );

    @Query("""
        SELECT p
        FROM PromotionRule p
        JOIN p.categoryExternalIds category
        WHERE p.storefrontId = :storefrontId AND p.isActive = true AND category = :categoryExternalId
    """)
    List<PromotionRule> findActiveByStorefrontIdAndCategoryExternalId(
        @Param("storefrontId") Long storefrontId,
        @Param("categoryExternalId") String categoryExternalId
    );

    /**
     * Empty when the rule doesn't exist or has no usage limit.
     */
    @Query("SELECT p.remainingUses FROM PromotionRule p WHERE p.id = :id AND p.hasUsageLimit = true")
    Optional<Integer> findRemainingUsesOfLimitedRule(@Param("id") Long id);

    /**
     * Gives one use back to each of the rules, never exceeding the rule's usage limit.
     */
    @Modifying
    @Query("""
        UPDATE PromotionRule p
        SET p.remainingUses = p.remainingUses + 1
        WHERE p.id IN :ids AND p.hasUsageLimit = true AND p.remainingUses < p.usageLimit
    """)
    int restoreRemainingUses(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("DELETE FROM PromotionRule p WHERE p.id = :id")
    int deletePromotionRuleById(@Param("id") Long id);
}
