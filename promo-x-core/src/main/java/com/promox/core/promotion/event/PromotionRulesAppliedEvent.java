package com.promox.core.promotion.event;

import java.util.List;
import java.util.UUID;

public record PromotionRulesAppliedEvent(
    UUID usageId,
    Long storefrontId,
    List<Long> appliedRuleIds
) {}
