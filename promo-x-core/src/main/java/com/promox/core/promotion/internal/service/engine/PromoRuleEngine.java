package com.promox.core.promotion.internal.service.engine;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.persistence.PromotionRuleBaseProjection;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromoRuleEngine {
    private final RuleUsageLimiter usageLimiter;

    public PromoRuleEngine(RuleUsageLimiter usageLimiter) {
        this.usageLimiter = usageLimiter;
    }

    // Assumes `rules` is already sorted by priority (highest first).
    public void calculate(EngineWorkspace workspace, List<PromotionRuleBaseProjection> rules) {
        workspace.resetToRawBaselines();

        for (PromotionRuleBaseProjection rule : rules) {
            boolean isEligible = rule.conditionTree().evaluate(workspace);

            if (isEligible) {
                workspace.logMatchedRule(rule.id(), rule.name());

                boolean actionApplied = applyWithinUsageLimit(rule, workspace);

                if (actionApplied) {
                    workspace.logAppliedRule(rule.id(), rule.name(), rule.hasUsageLimit());
                    // Only short-circuit if a rule actually modified the cart state
                    if (rule.isShortCircuit()) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * A limited rule takes its use before the action runs, so a rule that is out of uses never
     * touches the cart. The use is given back if the action then changes nothing or fails.
     */
    private boolean applyWithinUsageLimit(PromotionRuleBaseProjection rule, EngineWorkspace workspace) {
        if (!rule.hasUsageLimit()) {
            return rule.action().apply(workspace);
        }

        if (!usageLimiter.tryAcquire(rule.id())) {
            return false;
        }

        boolean actionApplied = false;
        try {
            actionApplied = rule.action().apply(workspace);
        } finally {
            if (!actionApplied) {
                usageLimiter.release(rule.id());
            }
        }
        return actionApplied;
    }
}
