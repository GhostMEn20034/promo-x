package com.promox.core.promotion.internal.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<PromotionRule, Long> {
    @EntityGraph(attributePaths = "productExternalIds")
    List<PromotionRule> findAllWithProductsBy();

    @EntityGraph(attributePaths = "productExternalIds")
    Optional<PromotionRule> findWithProductsById(Long id);
}
