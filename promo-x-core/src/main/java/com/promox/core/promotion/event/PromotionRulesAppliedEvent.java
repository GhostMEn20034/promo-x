package com.promox.core.promotion.event;

import java.util.List;

public record PromotionRulesAppliedEvent(
    Long storefrontId,
    List<Long> appliedRuleIds
) {}
