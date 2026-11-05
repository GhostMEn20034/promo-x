package com.promox.core.promotion.internal.persistence;

import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.condition.ConditionNode;

import java.io.Serializable;

/**
 * Flat projection of {@link PromotionRule} carrying only what the rule engine reads.
 * A concrete record (not an interface-based Spring Data projection) so it serializes
 * cleanly as a future Redis cache value.
 * <p>
 * Only the {@code hasUsageLimit} flag is included, never the remaining uses: the counter
 * changes on every application and would make the cached value stale immediately.
 */
public record PromotionRuleBaseProjection(
    Long id,
    Long storefrontId,
    String name,
    int priority,
    boolean isShortCircuit,
    boolean hasUsageLimit,
    ConditionNode conditionTree,
    ActionStrategy action
) implements Serializable {
    public static PromotionRuleBaseProjection from(PromotionRule rule) {
        return new PromotionRuleBaseProjection(
            rule.getId(),
            rule.getStorefrontId(),
            rule.getName(),
            rule.getPriority(),
            rule.isShortCircuit(),
            rule.hasUsageLimit(),
            rule.getConditionTree(),
            rule.getAction()
        );
    }
}
