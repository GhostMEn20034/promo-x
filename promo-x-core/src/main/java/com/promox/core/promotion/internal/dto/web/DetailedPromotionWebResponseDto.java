package com.promox.core.promotion.internal.dto.web;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.promox.core.product.DefaultProductDto;

import java.util.List;


public record DetailedPromotionWebResponseDto(
    @JsonUnwrapped PromotionBaseWebResponseDto base,
    List<DefaultProductDto> products
) {}
