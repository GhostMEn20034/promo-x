package com.promox.core.promotion.internal.dto;

import com.promox.core.promotion.internal.persistence.PromotionRuleUsageStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * @param expiresAt deadline for confirming a reserved usage; null when the usage was never reserved
 */
public record PromotionRuleUsageDto(
        UUID id,
        PromotionRuleUsageStatus status,
        @Nullable Instant expiresAt
) {}
