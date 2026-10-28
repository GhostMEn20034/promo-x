package com.promox.core.promotion.internal.service.eventlistener;

import com.promox.core.promotion.event.PromotionRuleCreatedEvent;
import com.promox.core.promotion.event.PromotionRuleDeleteEvent;
import com.promox.core.promotion.event.PromotionRuleUpdatedEvent;
import com.promox.core.promotion.event.PromotionRuleUsageUpdatedEvent;
import com.promox.core.promotion.internal.service.PromotionRuleCacheReader;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;


@Component
public class PromotionRuleCacheEventListener {
    private final CacheManager cacheManager;

    public PromotionRuleCacheEventListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @ApplicationModuleListener
    public void on(PromotionRuleCreatedEvent event) {
        Cache cache = cacheManager.getCache(PromotionRuleCacheReader.RULES_CACHE_NAME);
        if (cache != null) {
            cache.evictIfPresent(event.storefrontId().toString());
        }
    }

    @ApplicationModuleListener
    public void on(PromotionRuleUpdatedEvent event) {
        Cache cache = cacheManager.getCache(PromotionRuleCacheReader.RULES_CACHE_NAME);
        if (cache != null) {
            cache.evictIfPresent(event.storefrontId().toString());
        }
    }

    @ApplicationModuleListener
    public void on(PromotionRuleUsageUpdatedEvent event) {
        Cache cache = cacheManager.getCache(PromotionRuleCacheReader.RULES_CACHE_NAME);
        if (cache != null) {
            cache.evictIfPresent(event.storefrontId().toString());
        }
    }

    @ApplicationModuleListener
    public void on(PromotionRuleDeleteEvent event) {
        Cache cache = cacheManager.getCache(PromotionRuleCacheReader.RULES_CACHE_NAME);
        if (cache != null) {
            cache.evictIfPresent(event.storefrontId().toString());
        }
    }
}
