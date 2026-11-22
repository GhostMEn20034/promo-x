package com.promox.core.promotion;

import com.promox.core.promotion.internal.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.Set;


public interface PromotionLookupApi {
    Optional<DetailedPromotionDto> findById(Long promotionId);

    Page<PromotionSummaryDto> findAll(Long storefrontId, PromotionFilterRequest filter, Pageable pageable);

    PromotionRuleUsageStatsResponse getUsageStats(Long storefrontId, TimestampRange range);

    Page<PromotionRuleUsageAuditDto> getUsageAuditItemsByStorefrontId(
        Long storefrontId,
        Pageable pageable,
        Set<Long> matchedRuleIds,
        Set<Long> appliedRuleIds
    );
}
