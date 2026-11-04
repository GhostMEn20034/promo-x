package com.promox.core.promotion.internal.service;

import com.promox.core.promotion.TimestampRange;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditRepository;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageDailyCount;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageStats;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class PromotionStatsService {
    private final PromotionRuleUsageAuditRepository ruleUsageAuditRepository;

    public PromotionStatsService(PromotionRuleUsageAuditRepository ruleUsageAuditRepository) {
        this.ruleUsageAuditRepository = ruleUsageAuditRepository;
    }

    public PromotionRuleUsageStats getUsagesStatsPerStorefront(Long storefrontId, TimestampRange timestampRange) {
        return ruleUsageAuditRepository.aggregateUsageStats(storefrontId, timestampRange.from(), timestampRange.to());
    }

    public List<PromotionRuleUsageDailyCount> getUsagesCountPerDay(Long storefrontId, TimestampRange timestampRange) {
        return ruleUsageAuditRepository.countUsagesPerDay(storefrontId, timestampRange.from(), timestampRange.to());
    }
}
