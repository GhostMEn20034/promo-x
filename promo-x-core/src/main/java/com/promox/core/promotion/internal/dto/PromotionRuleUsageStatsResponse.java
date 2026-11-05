package com.promox.core.promotion.internal.dto;

import java.io.Serializable;
import java.util.List;

/**
 * @param totals      usage totals over the whole period
 * @param dailyCounts usage count of every day in the period, oldest first, with 0 for days without usage
 */
public record PromotionRuleUsageStatsResponse(
    PromotionRuleUsageStatsDto totals,
    List<PromotionRuleUsageDailyCountDto> dailyCounts
) implements Serializable {}
