package com.promox.core.promotion;

import com.promox.core.promotion.internal.dto.DetailedPromotionDto;
import com.promox.core.promotion.internal.dto.PromotionFilterRequest;
import com.promox.core.promotion.internal.dto.PromotionRuleUsageStatsResponse;
import com.promox.core.promotion.internal.dto.PromotionSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PromotionLookupApi {
    Optional<DetailedPromotionDto> findById(Long promotionId);

    Page<PromotionSummaryDto> findAll(Long storefrontId, PromotionFilterRequest filter, Pageable pageable);

    PromotionRuleUsageStatsResponse getUsageStats(Long storefrontId, TimestampRange range);
}
