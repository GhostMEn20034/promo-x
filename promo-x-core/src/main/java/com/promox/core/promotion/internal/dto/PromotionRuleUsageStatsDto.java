package com.promox.core.promotion.internal.dto;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;

/**
 * Totals of a storefront's confirmed usages over a period. The averages are null when there is no usage.
 */
public record PromotionRuleUsageStatsDto(
    long usageCount,
    long enteredPromoCodeCount,
    long registeredCustomerUsageCount,
    long anonymousCustomerUsageCount,
    @Nullable BigDecimal averageTotalDiscount,
    @Nullable BigDecimal averageSubtotalDiscount,
    @Nullable BigDecimal averageShippingDiscount
) {}
