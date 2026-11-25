package com.promox.core.promotion.internal.service;

import com.promox.core.infrastructure.logging.ScheduledJobLogging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Expires reserved rule usages that were not confirmed before their deadline.
 */
@Component
public class PromotionRuleUsageExpirationJob {
    private static final Logger log = LoggerFactory.getLogger(PromotionRuleUsageExpirationJob.class);

    private static final String JOB_NAME = "promotion-rule-usage-expiration";
    private static final int BATCH_SIZE = 100;

    private final PromotionRuleUsageService promotionRuleUsageService;

    public PromotionRuleUsageExpirationJob(PromotionRuleUsageService promotionRuleUsageService) {
        this.promotionRuleUsageService = promotionRuleUsageService;
    }

    // fixedDelay, not fixedRate: the next run starts a minute after this one ends, so runs never overlap.
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void expireOverdueUsages() {
        ScheduledJobLogging.run(log, JOB_NAME, this::expireInBatches);
    }

    private void expireInBatches() {
        int expired;
        int expiredTotal = 0;
        int batches = 0;
        do {
            // One transaction per batch, so a large backlog is not held in a single long transaction.
            expired = promotionRuleUsageService.expireOverdueUsages(BATCH_SIZE);
            expiredTotal += expired;
            batches++;
        } while (expired == BATCH_SIZE);

        log.atDebug()
                .addKeyValue("expiredUsages", expiredTotal)
                .addKeyValue("batches", batches)
                .log("Expired the overdue rule usages");
    }
}
