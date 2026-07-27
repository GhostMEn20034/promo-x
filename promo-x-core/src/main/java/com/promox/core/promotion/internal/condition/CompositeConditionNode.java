package com.promox.core.promotion.internal.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;


/**
 * Composite Node structural implementation. Handles nested AND/OR routing evaluation logical flows.
 */
public record CompositeConditionNode(
        @NotNull(message = "Logical operator is required")
        LogicalOperator operator,

        @NotEmpty(message = "Composite condition must contain at least one child condition")
        List<@Valid ConditionNode> children
) implements ConditionNode {

    public static final String TYPE_NAME = "COMPOSITE";
    public static final String DISPLAY_NAME = "Condition Group";
    private static final String DESC = "Combines multiple conditions together using logical AND or OR matching rules.";

    public enum LogicalOperator { AND, OR }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Map<Operator, String> getOperatorsDisplayName() {
        // Grouping nodes use LogicalOperator structurally,
        // so they return an empty map for leaf field operators.
        return Map.of();
    }

    @Override
    public String getDescription() {
        return DESC;
    }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        if (children == null || children.isEmpty()) return true;

        return switch (operator) {
            case AND -> children.stream().allMatch(node -> node.evaluate(workspace));
            case OR  -> children.stream().anyMatch(node -> node.evaluate(workspace));
        };
    }
}