package com.promox.core.promotion.internal.condition;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.promox.core.promotion.internal.context.EngineWorkspace;

import java.util.Map;
import java.util.Set;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public interface ConditionNode {
    String getTypeName();
    String getDisplayName();
    Map<Operator, String> getOperatorsDisplayName();

    // Default fallback so you don't HAVE to write a description if you don't want to
    default String getDescription() {
        return "";
    }

    default Set<Operator> getSupportedOperators() {
        return Set.of();
    }

    boolean evaluate(EngineWorkspace workspace);
}
