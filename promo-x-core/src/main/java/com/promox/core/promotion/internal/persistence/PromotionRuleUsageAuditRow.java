package com.promox.core.promotion.internal.persistence;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


/**
 * A usage audit with its usage's status, without the rules it matched and applied, which are read separately.
 * Filled by position from {@link PromotionRuleUsageAuditRepository}'s page queries.
 */
public record PromotionRuleUsageAuditRow(
    UUID usageId,
    String status,
    @Nullable String sessionId,
    @Nullable String customerId,
    String[] promoCodes,
    BigDecimal subtotalBefore,
    BigDecimal subtotalAfter,
    BigDecimal shippingCostBefore,
    BigDecimal shippingCostAfter,
    BigDecimal totalBefore,
    BigDecimal totalAfter,
    Instant createdAt
) {}
