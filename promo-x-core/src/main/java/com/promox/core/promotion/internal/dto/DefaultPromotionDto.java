package com.promox.core.promotion.internal.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Set;

public record DefaultPromotionDto(
    @JsonUnwrapped PromotionBaseDto base,
    Set<String> productExternalIds
) {}
