package com.promox.core.promotion.internal.service.condition;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ConditionNodeSpec(
    @JsonIgnore String typeName,
    String displayName,
    String description,
    Set<Operator> supportedOperators,
    Map<Operator, String> operatorsDisplayName,
    @JsonIgnore Class<? extends ConditionNode> implementationClass
) {
    public ConditionNodeSpec {
        Objects.requireNonNull(typeName, "typeName cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(implementationClass, "implementationClass cannot be null");

        description = description != null ? description : "";
        supportedOperators = supportedOperators != null ? Set.copyOf(supportedOperators) : Set.of();
        operatorsDisplayName = operatorsDisplayName != null ? Map.copyOf(operatorsDisplayName) : Map.of();
    }

    // Overloaded constructor for nodes without specific operator metadata (e.g., composite or flag nodes)
    public ConditionNodeSpec(
        String typeName,
        String displayName,
        String description,
        Class<? extends ConditionNode> implementationClass
    ) {
        this(typeName, displayName, description, Set.of(), Map.of(), implementationClass);
    }
}
