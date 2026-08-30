package com.promox.core.promotion.internal.dto.web;


import com.promox.core.promotion.internal.service.condition.ConditionNodeSpec;
import tools.jackson.databind.JsonNode;


public record ConditionNodeWebResponseDto(
    JsonNode node,
    ConditionNodeSpec spec
) {}
