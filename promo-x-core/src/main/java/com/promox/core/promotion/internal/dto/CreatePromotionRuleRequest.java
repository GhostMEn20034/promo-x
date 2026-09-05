package com.promox.core.promotion.internal.dto;

import com.promox.core.promotion.internal.dto.validators.ValidPromotionRuleCreationRequest;
import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.condition.ConditionNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


@ValidPromotionRuleCreationRequest
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

    @Min(value = 0, message = "usageLimit cannot be negative")
    Integer usageLimit,

    @Min(value = 0, message = "remainingUses cannot be negative")
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

        if (Boolean.FALSE.equals(hasUsageLimit)) {
            usageLimit = (usageLimit == null) ? 0 : usageLimit;
            remainingUses = (remainingUses == null) ? 0 : remainingUses;
        } else if (Boolean.TRUE.equals(hasUsageLimit)) {
            // Default remainingUses to usageLimit if omitted during creation
            if (remainingUses == null && usageLimit != null) {
                remainingUses = usageLimit;
            }
        }
    }
}
