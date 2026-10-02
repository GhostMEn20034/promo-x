package com.promox.core.promotion.internal.context;

public record AppliedRuleLog(
        Long ruleId,
        String ruleName,
        boolean hasUsageLimit
) {}
