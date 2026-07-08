package com.promox.core.promotion.internal.action;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.promox.core.promotion.internal.context.EngineWorkspace;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public interface ActionStrategy {
    /**
     * Mutates the execution workspace parameters to apply structural price deductions.
     */
    void apply(EngineWorkspace workspace);
}