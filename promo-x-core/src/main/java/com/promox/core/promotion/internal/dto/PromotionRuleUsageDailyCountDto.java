package com.promox.core.promotion.internal.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Number of confirmed usages on one UTC day.
 */
public record PromotionRuleUsageDailyCountDto(LocalDate date, long usageCount) implements Serializable {}
