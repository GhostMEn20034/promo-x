package com.promox.core.promotion.internal.service.engine;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromoRuleEngine {

    // Assumes `rules` is already sorted by priority (highest first).
    public void calculate(EngineWorkspace workspace, List<PromotionRule> rules) {
        workspace.resetToRawBaselines();

        for (PromotionRule rule : rules) {
            boolean isEligible = rule.getConditionTree().evaluate(workspace);

            if (isEligible) {
                workspace.logMatchedRule(rule.getId(), rule.getName());

                boolean actionApplied = rule.getAction().apply(workspace);

                if (actionApplied) {
                    workspace.logAppliedRule(rule.getId(), rule.getName());
                    // Only short-circuit if a rule actually modified the cart state
                    if (rule.isShortCircuit()) {
                        break;
                    }
                }
            }
        }
    }
}
