package com.promox.core.promotion.event;

import java.time.Instant;

public record PromotionRuleUsageUpdatedEvent(
        Long id,
        Long storefrontId,
        boolean hasUsageLimit,
        int usageLimit,
        int remainingUses,
        boolean remainingUsesMutated,
        Instant updatedAt
) {}
