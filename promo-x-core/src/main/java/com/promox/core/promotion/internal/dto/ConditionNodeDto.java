package com.promox.core.promotion.internal.dto;


import com.promox.core.promotion.internal.service.condition.ConditionNodeSpec;
import tools.jackson.databind.JsonNode;


public record ConditionNodeDto(
    JsonNode node,
    ConditionNodeSpec spec
) {}
