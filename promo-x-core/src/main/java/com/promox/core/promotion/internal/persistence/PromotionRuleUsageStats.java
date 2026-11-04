package com.promox.core.promotion.internal.persistence;

import java.math.BigDecimal;

public record PromotionRuleUsageStats(
    long usageCount,
    long enteredPromoCodeCount,
    long registeredCustomerUsageCount,
    long anonymousCustomerUsageCount,
    BigDecimal averageTotalDiscount,
    BigDecimal averageSubtotalDiscount,
    BigDecimal averageShippingDiscount
) {}
