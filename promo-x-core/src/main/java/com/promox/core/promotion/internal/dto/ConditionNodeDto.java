package com.promox.core.promotion.internal.dto;


import com.promox.core.promotion.internal.service.condition.ConditionNode;
import com.promox.core.promotion.internal.service.condition.ConditionNodeSpec;


public record ConditionNodeDto(
    ConditionNode node,
    ConditionNodeSpec spec
) {}
