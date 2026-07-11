package com.promox.core.promotion.internal.engine;

import com.promox.core.promotion.internal.action.ActionStrategy;
import com.promox.core.promotion.internal.context.EngineWorkspace;
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
            // Null condition blocks act as auto-passing rules
            boolean isEligible = rule.getConditionTree().evaluate(workspace);

            if (isEligible) {
                for (ActionStrategy action : rule.getActions()) {
                    action.apply(workspace);
                }
                workspace.logAppliedRule(rule.getId(), rule.getName());
                // Short-Circuit evaluation if this rule demands exclusivity
                if (rule.isShortCircuit()) {
                    break;
                }
            }
        }
    }
}
