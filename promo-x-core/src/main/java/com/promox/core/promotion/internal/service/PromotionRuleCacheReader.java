package com.promox.core.promotion.internal.service;

import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRuleBaseProjection;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Separate bean so {@code @Cacheable} goes through the Spring AOP proxy: called from
 * {@link PromotionService} as {@code this.findAllActiveSortedByPriority(...)} it would be a
 * self-invocation and the cache annotation would be silently ignored.
 */
@Service
public class PromotionRuleCacheReader {
    // Must match the promox.cache.ttls key in application.yaml
    public static final String RULES_CACHE_NAME = "promotion-rules";

    private final PromotionRepository promotionRepository;

    public PromotionRuleCacheReader(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Cacheable(cacheNames = RULES_CACHE_NAME, key = "#storefrontId")
    @Transactional(readOnly = true)
    public List<PromotionRuleBaseProjection> findAllActiveSortedByPriority(Long storefrontId) {
        return promotionRepository.findAllActiveProjectionsByStorefrontIdOrderByPriorityDesc(storefrontId);
    }
}
