package com.promox.core.promotion.internal.dto;

import java.time.Instant;


public record PromotionBaseDto(
    Long id,
    Long storefrontId,
    String name,
    int priority,
    boolean isShortCircuit,
    boolean isActive,
    ConditionNodeDto conditionTree,
    ActionDto action,
    Instant createdAt,
    Instant updatedAt
) {}
