package com.promox.core.promotion.internal.persistence;

import java.time.LocalDate;

/**
 * Number of confirmed usages on one UTC day.
 */
public record PromotionRuleUsageDailyCount(LocalDate date, long usageCount) {}
