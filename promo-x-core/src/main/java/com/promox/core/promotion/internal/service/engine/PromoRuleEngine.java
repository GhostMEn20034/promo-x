package com.promox.core.promotion.internal.service.engine;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.persistence.PromotionRuleBaseProjection;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromoRuleEngine {

    // Assumes `rules` is already sorted by priority (highest first).
    public void calculate(EngineWorkspace workspace, List<PromotionRuleBaseProjection> rules) {
        workspace.resetToRawBaselines();

        for (PromotionRuleBaseProjection rule : rules) {
            boolean isEligible = rule.conditionTree().evaluate(workspace);

            if (isEligible) {
                workspace.logMatchedRule(rule.id(), rule.name());

                boolean actionApplied = rule.action().apply(workspace);

                if (actionApplied) {
                    workspace.logAppliedRule(rule.id(), rule.name());
                    // Only short-circuit if a rule actually modified the cart state
                    if (rule.isShortCircuit()) {
                        break;
                    }
                }
            }
        }
    }
}
