package com.promox.core.promotion.internal.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<PromotionRule, Long>, JpaSpecificationExecutor<PromotionRule> {
    @EntityGraph(attributePaths = "productExternalIds")
    List<PromotionRule> findAllWithProductsBy();

    @EntityGraph(attributePaths = "productExternalIds")
    Optional<PromotionRule> findWithProductsById(Long id);

    @Modifying
    @Query("DELETE FROM PromotionRule p WHERE p.id = :id")
    int deletePromotionRuleById(@Param("id") Long id);
}
