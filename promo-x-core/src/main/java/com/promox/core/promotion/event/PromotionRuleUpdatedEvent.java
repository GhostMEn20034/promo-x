package com.promox.core.promotion.event;


import java.time.Instant;
import java.util.Set;

public record PromotionRuleUpdatedEvent(
        Long id,
        Long storefrontId,
        String name,
        int priority,
        boolean isShortCircuit,
        boolean isActive,
        boolean hasUsageLimit,
        int usageLimit,
        int remainingUses,
        Set<String> productExternalIds,
        Set<String> categoryExternalIds,
        Instant createdAt,
        Instant updatedAt
) {}
