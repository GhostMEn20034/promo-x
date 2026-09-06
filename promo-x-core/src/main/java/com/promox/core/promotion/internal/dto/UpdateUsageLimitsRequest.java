package com.promox.core.promotion.internal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


public record UpdateUsageLimitsRequest(
        @NotNull(message = "hasUsageLimit is required")
        Boolean hasUsageLimit,

        @Positive(message = "usageLimit must be positive")
        Integer usageLimit,

        @Min(value = 0, message = "remainingUses must be at least 0")
        Integer remainingUses,

        @NotNull
        Boolean mutateRemainingUses
) {
    public UpdateUsageLimitsRequest {
        if (mutateRemainingUses == null) {
            mutateRemainingUses = false;
        }

        if (usageLimit == null) {
            usageLimit = 1;
        }

        if (remainingUses == null) {
            remainingUses = usageLimit;
        }

        if (mutateRemainingUses && remainingUses > usageLimit) {
            remainingUses = usageLimit;
        }
    }
}
