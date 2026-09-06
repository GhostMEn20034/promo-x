package com.promox.core.promotion.internal.dto;

import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.condition.ConditionNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


public record CreatePromotionRuleRequest(
    @NotBlank
    @Size(max = 255)
    String name,
    @NotNull
    @Positive
    int priority,

    @NotNull
    boolean isShortCircuit,

    Boolean isActive,

    @NotNull(message = "hasUsageLimit is required")
    Boolean hasUsageLimit,

    @Positive(message = "usageLimit must be positive")
    Integer usageLimit,

    @Positive(message = "remainingUses must be positive")
    Integer remainingUses,

    @NotNull(message = "Condition tree is required")
    @Valid
    ConditionNode conditionTree,

    @NotNull(message = "Action strategy is required")
    @Valid
    ActionStrategy action
) {
    public CreatePromotionRuleRequest {
        if (isActive == null) {
            isActive = true;
        }

        if (usageLimit == null) {
            usageLimit = 1;
        }

        if (remainingUses == null) {
            remainingUses = usageLimit;
        } else if (remainingUses > usageLimit) {
            remainingUses = usageLimit;
        }
    }
}
