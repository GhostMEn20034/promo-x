package com.promox.core.promotion.internal.dto.web;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Set;

public record DefaultPromotionWebResponseDto(
    @JsonUnwrapped PromotionBaseWebResponseDto base,
    Set<String> productExternalIds
) {}
