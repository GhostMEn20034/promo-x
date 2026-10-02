package com.promox.core.promotion.event;

import java.util.List;
import java.util.UUID;

/**
 * @param limitedRuleIds rules whose held use is given back because the reservation expired
 */
public record PromotionRuleUsageExpiredEvent(
    UUID usageId,
    Long storefrontId,
    List<Long> limitedRuleIds
) {}
