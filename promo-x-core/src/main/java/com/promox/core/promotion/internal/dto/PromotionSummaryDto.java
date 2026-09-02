package com.promox.core.promotion.internal.dto;

import java.time.Instant;

public record PromotionSummaryDto(
    Long id,
    Long storefrontId,
    String name,
    int priority,
    boolean isShortCircuit,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}
