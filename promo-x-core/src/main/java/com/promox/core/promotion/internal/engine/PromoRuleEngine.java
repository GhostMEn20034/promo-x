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
                workspace.logMatchedRule(rule.getId(), rule.getName());

                boolean ruleActuallyApplied = false;
                for (ActionStrategy action : rule.getActions()) {
                    boolean actionApplied = action.apply(workspace);
                    if (actionApplied) {
                        ruleActuallyApplied = true;
                    }
                }

                if (ruleActuallyApplied) {
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
