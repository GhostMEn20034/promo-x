package com.promox.core.promotion.internal.dto;

import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.condition.ConditionNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


public record UpdatePromotionRuleRequest(
    @NotBlank
    @Size(max = 255)
    String name,

    @NotNull
    @Positive
    int priority,

    @NotNull
    boolean isShortCircuit,

    Boolean isActive,

    @NotNull(message = "Condition tree is required")
    @Valid
    ConditionNode conditionTree,

    @NotNull(message = "Action strategy is required")
    @Valid
    ActionStrategy action
) {}
