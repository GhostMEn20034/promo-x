package com.promox.core.promotion.internal.service;

import com.promox.core.product.DefaultProductDto;
import com.promox.core.product.ProductLookupApi;
import com.promox.core.promotion.PromotionLookupApi;
import com.promox.core.promotion.internal.PromotionMapper;
import com.promox.core.promotion.internal.dto.*;
import com.promox.core.promotion.TimestampRange;

import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditRow;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditRuleRow;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


@Service
public class PromotionLookupApiImpl implements PromotionLookupApi {
    // Must match the app.cache.ttls key in application.yaml
    public static final String USAGE_STATS_CACHE_NAME = "promotion-usage-stats";

    private final PromotionService promotionService;
    private final PromotionMapper promotionMapper;
    private final ProductLookupApi productLookupApi;
    private final PromotionStatsService promotionStatsService;
    private final PromotionRuleUsageAuditService promotionRuleUsageAuditService;

    public PromotionLookupApiImpl(
        PromotionService promotionService,
        PromotionMapper promotionMapper,
        ProductLookupApi productLookupApi,
        PromotionStatsService promotionStatsService,
        PromotionRuleUsageAuditService promotionRuleUsageAuditService
    ) {
        this.promotionService = promotionService;
        this.promotionMapper = promotionMapper;
        this.productLookupApi = productLookupApi;
        this.promotionStatsService = promotionStatsService;
        this.promotionRuleUsageAuditService = promotionRuleUsageAuditService;
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

    /**
     * Reads the page of audits first, then the matched and applied rules of only that page's usages, and puts them
     * together. Read-only, so the queries share a connection and nothing is flushed.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<PromotionRuleUsageAuditDto> getUsageAuditItemsByStorefrontId(
        Long storefrontId,
        Pageable pageable,
        Set<Long> matchedRuleIds,
        Set<Long> appliedRuleIds
    ) {
        Page<PromotionRuleUsageAuditRow> audits = promotionRuleUsageAuditService.getWithoutRulesByStorefrontId(
            storefrontId,
            pageable,
            matchedRuleIds,
            appliedRuleIds
        );
        List<UUID> usageIds = audits.map(PromotionRuleUsageAuditRow::usageId).getContent();
        Map<UUID, List<PromotionRuleUsageAuditRuleRow>> matched = promotionRuleUsageAuditService.getMatchedRules(usageIds);
        Map<UUID, List<PromotionRuleUsageAuditRuleRow>> applied = promotionRuleUsageAuditService.getAppliedRules(usageIds);

        return audits.map(audit -> promotionMapper.toUsageAuditDto(
            audit,
            matched.getOrDefault(audit.usageId(), List.of()),
            applied.getOrDefault(audit.usageId(), List.of())
        ));
    }
}
