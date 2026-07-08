package com.promox.core.promotion.internal.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import java.util.List;


/**
 * Composite Node structural implementation. Handles nested AND/OR routing evaluation logical flows.
 */
record CompositeConditionNode(
        LogicalOperator operator,
        List<ConditionNode> children
) implements ConditionNode {

    public enum LogicalOperator { AND, OR }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        if (children == null || children.isEmpty()) return true;

        return switch (operator) {
            case AND -> children.stream().allMatch(node -> node.evaluate(workspace));
            case OR  -> children.stream().anyMatch(node -> node.evaluate(workspace));
        };
    }
}