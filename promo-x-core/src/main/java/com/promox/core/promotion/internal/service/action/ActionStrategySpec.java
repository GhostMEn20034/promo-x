package com.promox.core.promotion.internal.service.action;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ActionStrategySpec(
    @JsonIgnore String typeName,
    String displayName,
    String description,
    @JsonIgnore Class<? extends ActionStrategy> implementationClass
) {}
