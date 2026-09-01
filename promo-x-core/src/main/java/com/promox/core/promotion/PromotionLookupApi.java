package com.promox.core.promotion;

import com.promox.core.promotion.internal.dto.DetailedPromotionDto;

import java.util.Optional;

public interface PromotionLookupApi {
    Optional<DetailedPromotionDto> findById(Long promotionId);
}
