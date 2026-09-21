package com.promox.core.promotion.internal.service.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


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

    public static final ConditionNodeSpec SPEC = new ConditionNodeSpec(
        TYPE_NAME,
        "Condition Group",
        "Combines multiple conditions together using logical AND or OR matching rules.",
        CompositeConditionNode.class
    );

    public enum LogicalOperator { AND, OR }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public Set<String> getProductIdentifiers() {
        return children == null ? Set.of() : children.stream()
                                             .filter(Objects::nonNull)
                                             .flatMap(child -> child.getProductIdentifiers().stream())
                                             .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> getCategoryIds() {
        return children == null ? Set.of() : children.stream()
                                             .filter(Objects::nonNull)
                                             .flatMap(child -> child.getCategoryIds().stream())
                                             .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        if (children == null || children.isEmpty()) {
            return true;
        }

        return switch (operator) {
            case AND -> children.stream().allMatch(node -> node.evaluate(workspace));
            case OR  -> children.stream().anyMatch(node -> node.evaluate(workspace));
        };
    }
}