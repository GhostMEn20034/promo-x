package com.promox.core.promotion.internal.service;

import com.promox.core.product.DefaultProductDto;
import com.promox.core.product.ProductLookupApi;
import com.promox.core.promotion.PromotionLookupApi;
import com.promox.core.promotion.internal.PromotionMapper;
import com.promox.core.promotion.internal.dto.DetailedPromotionDto;
import com.promox.core.promotion.internal.dto.PromotionFilterRequest;
import com.promox.core.promotion.internal.dto.PromotionRuleUsageDailyCountDto;
import com.promox.core.promotion.internal.dto.PromotionRuleUsageStatsDto;
import com.promox.core.promotion.internal.dto.PromotionRuleUsageStatsResponse;
import com.promox.core.promotion.internal.dto.PromotionSummaryDto;
import com.promox.core.promotion.TimestampRange;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


@Service
public class PromotionLookupApiImpl implements PromotionLookupApi {
    // Must match the promox.cache.ttls key in application.yaml
    public static final String USAGE_STATS_CACHE_NAME = "promotion-usage-stats";

    private final PromotionService promotionService;
    private final PromotionMapper promotionMapper;
    private final ProductLookupApi productLookupApi;
    private final PromotionStatsService promotionStatsService;

    public PromotionLookupApiImpl(
        PromotionService promotionService,
        PromotionMapper promotionMapper,
        ProductLookupApi productLookupApi,
        PromotionStatsService promotionStatsService
    ) {
        this.promotionService = promotionService;
        this.promotionMapper = promotionMapper;
        this.productLookupApi = productLookupApi;
        this.promotionStatsService = promotionStatsService;
    }

    @Override
    public Optional<DetailedPromotionDto> findById(Long promotionId) {
        return promotionService.findWithProductsById(promotionId).map(promotion -> {
            List<DefaultProductDto> products = productLookupApi.findAllByExternalIds(
                promotion.getProductExternalIds(),
                promotion.getStorefrontId()
            );
            return promotionMapper.toDetailedDto(promotion, products);
        });
    }

    @Override
    public Page<PromotionSummaryDto> findAll(
        Long storefrontId,
        PromotionFilterRequest filter,
        Pageable pageable
    ) {
        return promotionService.findAll(storefrontId, filter, pageable)
            .map(promotionMapper::toSummaryDto);
    }

    @Override
    @Cacheable(
        cacheNames = USAGE_STATS_CACHE_NAME,
        key = "#storefrontId + ':' + #range.from() + ':' + #range.to()",
        sync = true
    )
    public PromotionRuleUsageStatsResponse getUsageStats(Long storefrontId, TimestampRange range) {
        PromotionRuleUsageStatsDto totals = promotionMapper.toUsageStatsDto(
            promotionStatsService.getUsagesStatsPerStorefront(storefrontId, range)
        );
        List<PromotionRuleUsageDailyCountDto> dailyCounts = promotionStatsService
            .getUsagesCountPerDay(storefrontId, range)
            .stream()
            .map(promotionMapper::toUsageDailyCountDto)
            .toList();

        return new PromotionRuleUsageStatsResponse(totals, dailyCounts);
    }
}
