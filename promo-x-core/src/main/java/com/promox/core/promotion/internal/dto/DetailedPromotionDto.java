package com.promox.core.promotion.internal.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.promox.core.product.DefaultProductDto;

import java.util.List;
import java.util.Set;

public record DetailedPromotionDto(
    @JsonUnwrapped PromotionBaseDto base,
    List<DefaultProductDto> products
) {}
