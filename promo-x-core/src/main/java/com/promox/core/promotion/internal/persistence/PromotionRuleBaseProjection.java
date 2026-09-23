package com.promox.core.promotion.internal.persistence;

import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.condition.ConditionNode;

/**
 * Flat projection of {@link PromotionRule} carrying only what the rule engine reads.
 * A concrete record (not an interface-based Spring Data projection) so it serializes
 * cleanly as a future Redis cache value.
 */
public record PromotionRuleBaseProjection(
    Long id,
    Long storefrontId,
    String name,
    int priority,
    boolean isShortCircuit,
    ConditionNode conditionTree,
    ActionStrategy action
) {
    public static PromotionRuleBaseProjection from(PromotionRule rule) {
        return new PromotionRuleBaseProjection(
            rule.getId(),
            rule.getStorefrontId(),
            rule.getName(),
            rule.getPriority(),
            rule.isShortCircuit(),
            rule.getConditionTree(),
            rule.getAction()
        );
    }
}
