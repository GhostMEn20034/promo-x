package com.promox.core.promotion.internal.service.condition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.promox.core.promotion.internal.context.EngineWorkspace;

import java.util.Set;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public interface ConditionNode {
    @JsonIgnore
    String getTypeName();
    /**
     * Returns products' identifiers (Currently external IDs) mentioned in the condition node
     */
    @JsonIgnore
    default Set<String> getProductIdentifiers() {
        return Set.of();
    }

    /**
     * Returns categories' identifiers mentioned in the condition node
     */
    @JsonIgnore
    default Set<String> getCategoryIds() {
        return Set.of();
    }

    boolean evaluate(EngineWorkspace workspace);
}
