package com.promox.core.promotion.internal.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.action.ActionStrategySpec;

public record ActionDto(
    ActionStrategy action,
    ActionStrategySpec spec
) {}
