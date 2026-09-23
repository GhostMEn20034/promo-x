package com.promox.core.promotion.internal.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<PromotionRule, Long>, JpaSpecificationExecutor<PromotionRule> {
    @EntityGraph(attributePaths = {"productExternalIds", "categoryExternalIds"})
    List<PromotionRule> findAllWithProductsBy();

    @EntityGraph(attributePaths = {"productExternalIds", "categoryExternalIds"})
    Optional<PromotionRule> findWithProductsById(Long id);

    @Query("""
        SELECT new com.promox.core.promotion.internal.persistence.PromotionRuleBaseProjection(
            p.id, p.storefrontId, p.name, p.priority, p.isShortCircuit, p.conditionTree, p.action
        )
        FROM PromotionRule p
        WHERE p.storefrontId = :storefrontId AND p.isActive = true
        ORDER BY p.priority DESC
    """)
    List<PromotionRuleBaseProjection> findAllActiveProjectionsByStorefrontIdOrderByPriorityDesc(
        @Param("storefrontId") Long storefrontId
    );

    @Modifying
    @Query("DELETE FROM PromotionRule p WHERE p.id = :id")
    int deletePromotionRuleById(@Param("id") Long id);
}
