package com.promox.core.promotion.internal.engine;

import com.promox.core.promotion.internal.action.ActionStrategy;
import com.promox.core.promotion.internal.condition.ConditionNode;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class PromotionRule {
    private String id;
    private String storefrontId;
    private String name;
    private int priority;

    // Engine Short-Circuit Strategy Anchor: If true, no lower-priority rules can run.
    private boolean shortCircuit;

    // Persisted as JSON text in the DB database column
    private ConditionNode conditionTree;
    private List<ActionStrategy> actions;
}