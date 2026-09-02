package com.promox.core.promotion.internal.dto;

import java.time.Instant;


public record PromotionFilterRequest(
    Instant createdFrom,
    Instant createdTo,
    Boolean isShortCircuit,
    Boolean isActive
) {}
