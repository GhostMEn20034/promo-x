package com.promox.core.promotion.internal.dto.web;

import com.promox.core.promotion.internal.dto.ActionDto;

import java.time.Instant;


public record PromotionBaseWebResponseDto(
    Long id,
    Long storefrontId,
    String name,
    int priority,
    boolean isShortCircuit,
    boolean isActive,
    ConditionNodeWebResponseDto conditionTree,
    ActionDto action,
    Instant createdAt,
    Instant updatedAt
) {}
