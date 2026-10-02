package com.promox.core.promotion.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromotionRuleUsageAuditProductRepository extends JpaRepository<PromotionRuleUsageAuditProduct, UUID> {
}
