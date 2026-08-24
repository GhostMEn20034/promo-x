package com.promox.core.promotion.internal.service.action;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.promox.core.promotion.internal.context.EngineWorkspace;

import java.util.Set;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public interface ActionStrategy {
    @JsonIgnore
    String getTypeName();

    /**
     * Returns products' identifiers (Currently external IDs) mentioned in the condition node
     */
    @JsonIgnore
    default Set<String> getProductIdentifiers() {
        return Set.of();
    }

    boolean apply(EngineWorkspace workspace);
}