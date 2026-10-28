package com.promox.core.promotion.internal.service.eventlistener;

import com.promox.core.promotion.event.PromotionRuleUsageExpiredEvent;
import com.promox.core.promotion.event.PromotionRulesAppliedEvent;
import com.promox.core.promotion.internal.service.PromotionService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;


@Component
public class PromotionRuleUsageEventListener {
    private final PromotionService promotionService;

    public PromotionRuleUsageEventListener(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @ApplicationModuleListener
    public void on(PromotionRulesAppliedEvent event) {
        promotionService.reduceRemainingUses(event.appliedRuleIds());
    }

    @ApplicationModuleListener
    public void on(PromotionRuleUsageExpiredEvent event) {
        promotionService.restoreRemainingUses(event.limitedRuleIds());
    }
}
