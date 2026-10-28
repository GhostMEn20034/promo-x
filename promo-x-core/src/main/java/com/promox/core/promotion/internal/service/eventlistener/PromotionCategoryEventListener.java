package com.promox.core.promotion.internal.service.eventlistener;

import com.promox.core.category.event.CategoryUpdatedEvent;
import com.promox.core.promotion.internal.service.PromotionService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;


@Component
public class PromotionCategoryEventListener {
    private final PromotionService promotionService;

    public PromotionCategoryEventListener(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    // Rules reference categories by external ID, so only a changed external ID leaves them stale.
    @ApplicationModuleListener
    public void on(CategoryUpdatedEvent event) {
        if (event.previousExternalId().equals(event.externalId())) {
            return;
        }

        promotionService.deactivateRulesReferencingCategory(event.storefrontId(), event.previousExternalId());
    }
}
