package com.promox.core.promotion.internal.service.engine;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class PromoRuleEngine {

    public void calculate(EngineWorkspace workspace, List<PromotionRule> rules) {
        workspace.resetToRawBaselines();

        // Sort rules: Highest priority executing first
        List<PromotionRule> sortedRules = rules.stream()
                .sorted(Comparator.comparingInt(PromotionRule::getPriority).reversed())
                .toList();

        for (PromotionRule rule : sortedRules) {
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
