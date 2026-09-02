package com.promox.core.promotion.internal.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<PromotionRule, Long>, JpaSpecificationExecutor<PromotionRule> {
    @EntityGraph(attributePaths = "productExternalIds")
    List<PromotionRule> findAllWithProductsBy();

    @EntityGraph(attributePaths = "productExternalIds")
    Optional<PromotionRule> findWithProductsById(Long id);
}
