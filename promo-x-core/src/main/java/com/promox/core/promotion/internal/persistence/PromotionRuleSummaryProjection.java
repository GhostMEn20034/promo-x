package com.promox.core.promotion.internal.persistence;

import java.time.Instant;


public interface PromotionRuleSummaryProjection {
    Long getId();
    Long getStorefrontId();
    String getName();
    int getPriority();
    boolean getIsShortCircuit();
    boolean getIsActive();
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
