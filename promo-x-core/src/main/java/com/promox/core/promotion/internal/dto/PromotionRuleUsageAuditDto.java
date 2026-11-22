package com.promox.core.promotion.internal.dto;

import com.promox.core.promotion.internal.persistence.PromotionRuleUsageStatus;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;


/**
 * One audited evaluation: the cart's totals before and after, the usage's current status, and the rules it matched
 * and applied.
 *
 * @param matchedRules rules whose conditions matched, from the highest priority down, whether or not they applied
 * @param appliedRules rules whose action changed the cart, in the order they were applied
 * @param createdAt    when the cart was evaluated
 */
public record PromotionRuleUsageAuditDto(
    UUID usageId,
    PromotionRuleUsageStatus status,
    @Nullable String sessionId,
    @Nullable String customerId,
    Set<String> promoCodes,
    BigDecimal subtotalBefore,
    BigDecimal subtotalAfter,
    BigDecimal shippingCostBefore,
    BigDecimal shippingCostAfter,
    BigDecimal totalBefore,
    BigDecimal totalAfter,
    List<PromotionRuleRefDto> matchedRules,
    List<PromotionRuleRefDto> appliedRules,
    Instant createdAt
) {}
