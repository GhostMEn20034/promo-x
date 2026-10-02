package com.promox.core.promotion.internal.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Expires reserved rule usages that were not confirmed before their deadline.
 */
@Component
public class PromotionRuleUsageExpirationJob {
    private static final int BATCH_SIZE = 100;

    private final PromotionRuleUsageService promotionRuleUsageService;

    public PromotionRuleUsageExpirationJob(PromotionRuleUsageService promotionRuleUsageService) {
        this.promotionRuleUsageService = promotionRuleUsageService;
    }

    // fixedDelay, not fixedRate: the next run starts a minute after this one ends, so runs never overlap.
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void expireOverdueUsages() {
        int expired;
        do {
            // One transaction per batch, so a large backlog is not held in a single long transaction.
            expired = promotionRuleUsageService.expireOverdueUsages(BATCH_SIZE);
        } while (expired == BATCH_SIZE);
    }
}
